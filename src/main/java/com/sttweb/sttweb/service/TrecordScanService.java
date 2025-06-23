// src/main/java/com/sttweb/sttweb/service/TrecordScanService.java
package com.sttweb.sttweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.entity.TrecordEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.repository.TrecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrecordScanService {

  private final TrecordRepository trecordRepository;
  private final TmemberRepository memberRepository; // ← 회원 정보를 조회하기 위해 추가
  private final ObjectMapper objectMapper = new ObjectMapper();

  // 윈도우상의 RecOnData 경로를 찾기 위해 C:, D:, E: 드라이브 순으로 검색
  private static final String[] DRIVES         = { "C:", "D:", "E:" };
  private static final String   ROOT_SUBPATH   = "\\RecOnData";
  private static final String   WAV_EXTENSION  = ".wav";
  private static final String   JSON_EXTENSION = ".json";

  /**
   * C:, D:, E: 드라이브 중에서 \\RecOnData 폴더를 찾아 Path로 반환.
   * 없으면 null 반환.
   */
  private Path findRecOnDataRoot() {
    for (String drive : DRIVES) {
      Path candidate = Paths.get(drive + ROOT_SUBPATH);
      if (Files.exists(candidate) && Files.isDirectory(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * RecOnData 하위의 모든 .wav 파일을 스캔한 뒤,
   * 1) 먼저 파일명으로부터 number1, number2, callStartDateTime, ioDiscdVal 을 채우고,
   * 2) 같은 폴더에 JSON 메타가 있으면 JSON 값을 덮어씀
   * 3) JSON에도 없으면 WAV 헤더에서 재생시간을 계산해서 callEndDateTime, audioPlayTime를 채움
   * 4) callStatus는 JSON에 없으면 기본값 "OK" 설정
   * 5) 파일명+JSON을 통해 number1(ext)를 채웠으면, DB에 없는 녹취라면 INSERT 시에
   *    → ownerMemberSeq, branchSeq(회원의 지점)를 함께 저장
   *
   * @return 실제로 INSERT된 레코드 수
   */
  @Transactional
  public int scanAndSaveNewRecords() throws IOException {
    Path rootDir = findRecOnDataRoot();
    if (rootDir == null) {
      // RecOnData 폴더를 못 찾았으면 0건 리턴
      return 0;
    }

    // 1) 기존 DB에 저장된 audioFileDir(Path) 일람을 미리 구해 두기
    List<TrecordEntity> existingList = trecordRepository.findAll();
    Set<String> existingRelPaths = new HashSet<>();
    for (TrecordEntity e : existingList) {
      existingRelPaths.add(e.getAudioFileDir());
    }

    // 2) RecOnData 하위의 모든 .wav 파일을 찾아 리스트에 모으기
    List<Path> allWavFiles = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(rootDir)) {
      walk.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(WAV_EXTENSION))
          .forEach(allWavFiles::add);
    }

    int insertedCount = 0;
    // (A) 파일명에서 날짜를 파싱하기 위한 포맷 (예: "20240125103148")
    DateTimeFormatter fileNameDtFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // (B) JSON 내부 issueDate 파싱 포맷 (예: "2025년 5월 20일")
    DateTimeFormatter issueDateFmt   = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    for (Path wavFullPath : allWavFiles) {
      // 3) <RecOnData 폴더> 이후의 상대경로로 변환
      Path relative = rootDir.relativize(wavFullPath);
      // DB에 저장할 때는 "../20240125/0333-...wav" 형태로 하기로 약속
      String audioFileDir = "../" + relative.toString().replace("\\", "/");

      // 이미 DB에 동일한 경로로 들어가 있으면 skip
      if (existingRelPaths.contains(audioFileDir)) {
        continue;
      }

      // 4) TrecordEntity 새 인스턴스를 만들고, '파일명 파싱'으로 기본 필드들을 채워 둔다.
      TrecordEntity rec = new TrecordEntity();
      rec.setAudioFileDir(audioFileDir);
      parseFromFilename(wavFullPath.getFileName().toString(), rec, fileNameDtFmt);

      // 5) WAV 파일과 같은 디렉토리에 동일 이름의 .json 메타파일이 있는지 확인
      Path jsonCandidate = findMatchingJson(wavFullPath);

      // 6) JSON이 있으면, JSON 속 값으로 덮어쓰기
      if (jsonCandidate != null) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                new FileInputStream(jsonCandidate.toFile()), Charset.forName("MS949"))
        )) {
          JsonNode root        = objectMapper.readTree(reader);
          JsonNode paramsNode  = root.path("params");
          JsonNode userdata    = paramsNode.path("userdata");

          // (1) issueDate → callStartDateTime 덮어쓰기
          if (userdata.has("issueDate") && !userdata.get("issueDate").asText().isBlank()) {
            String issueDateText = userdata.get("issueDate").asText().trim();
            try {
              LocalDate datePart = LocalDate.parse(issueDateText, issueDateFmt);
              // JSON에는 시각 정보가 없으므로, 자정(00:00:00)으로 들어간다.
              rec.setCallStartDateTime(Timestamp.valueOf(datePart.atStartOfDay()));
            } catch (Exception e) {
              // 만약 실제 JSON에 "yyyy-MM-dd HH:mm:ss" 등의 포맷이 들어온다면,
              // LocalDateTime.parse(...)로 다시 파싱해서 rec.setCallStartDateTime(...) 하면 됩니다.
            }
          }
          // (1-1) 최상위 callStartDateTime이 있다면 이 값으로 덮어쓰기
          else if (root.has("callStartDateTime") && !root.get("callStartDateTime").asText().isBlank()) {
            LocalDateTime startLdt = LocalDateTime.parse(root.get("callStartDateTime").asText());
            rec.setCallStartDateTime(Timestamp.valueOf(startLdt));
          }

          // (2) callEndDateTime → 덮어쓰기 (JSON 최상위)
          if (root.has("callEndDateTime") && !root.get("callEndDateTime").asText().isBlank()) {
            LocalDateTime endLdt = LocalDateTime.parse(root.get("callEndDateTime").asText());
            rec.setCallEndDateTime(Timestamp.valueOf(endLdt));
          }

          // (3) playTime → audioPlayTime (JSON 최상위)
          if (root.has("playTime") && !root.get("playTime").asText().isBlank()) {
            rec.setAudioPlayTime(Time.valueOf(root.get("playTime").asText()));
          }

          // (4) IOvalue: "I" / "O" → "발신"/"수신" 덮어쓰기
          if (userdata.has("IOvalue") && !userdata.get("IOvalue").asText().isBlank()) {
            String ioVal = userdata.get("IOvalue").asText().trim().toUpperCase();
            if ("I".equals(ioVal)) {
              rec.setIoDiscdVal("발신");
            } else if ("O".equals(ioVal)) {
              rec.setIoDiscdVal("수신");
            }
          }
          // (4-1) 최상위 io 필드가 있다면 fallback
          else if (root.has("io") && !root.get("io").asText().isBlank()) {
            String ioVal = root.get("io").asText().trim().toUpperCase();
            if ("I".equals(ioVal))      rec.setIoDiscdVal("발신");
            else if ("O".equals(ioVal)) rec.setIoDiscdVal("수신");
          }

          // (5) speaker1 → number1 덮어쓰기
          if (userdata.has("speaker1") && !userdata.get("speaker1").asText().isBlank()) {
            rec.setNumber1(userdata.get("speaker1").asText().trim());
          }
          // (5-1) 최상위 ext 필드가 있다면 fallback
          else if (root.has("ext") && !root.get("ext").asText().isBlank()) {
            rec.setNumber1(root.get("ext").asText().trim());
          }

          // (6) speaker2 → number2 덮어쓰기
          if (userdata.has("speaker2") && !userdata.get("speaker2").asText().isBlank()) {
            rec.setNumber2(userdata.get("speaker2").asText().trim());
          }
          // (6-1) 최상위 other 필드가 있다면 fallback
          else if (root.has("other") && !root.get("other").asText().isBlank()) {
            rec.setNumber2(root.get("other").asText().trim());
          }

          // (7) callStatus 덮어쓰기: userdata.status 먼저 보고, 없다면 최상위 callStatus
          if (userdata.has("status") && !userdata.get("status").asText().isBlank()) {
            rec.setCallStatus(userdata.get("status").asText().trim());
          } else if (root.has("callStatus") && !root.get("callStatus").asText().isBlank()) {
            rec.setCallStatus(root.get("callStatus").asText().trim());
          }
        }
        catch (Exception ex) {
          // JSON 파싱 중 오류가 발생해도,
          // 이미 파일명으로 채운 필드들은 그대로 유지됩니다.
        }
      }

      // 7) 이제 “파일명+JSON”에도 callEndDateTime/ audioPlayTime 정보가 없으면,
      //    WAV 헤더를 직접 읽어서 재생시간(duration)과 통화 종료 시각을 계산해 넣는다.
      if (rec.getCallStartDateTime() != null && rec.getCallEndDateTime() == null) {
        try {
          File wavFile = wavFullPath.toFile();
          AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile);
          AudioFormat format = ais.getFormat();
          long frames       = ais.getFrameLength();
          double frameRate  = format.getFrameRate();
          double durationSec = frames / frameRate; // 초 단위 재생시간
          ais.close();

          // 재생시간(Time) 설정 (HH:mm:ss)
          long secondsTotal = (long) Math.round(durationSec);
          LocalTime lt = LocalTime.ofSecondOfDay(secondsTotal);
          rec.setAudioPlayTime(Time.valueOf(lt));

          // callEndDateTime = callStartDateTime + 재생시간
          long startMillis = rec.getCallStartDateTime().getTime();
          long endMillis   = startMillis + (long)(durationSec * 1000);
          rec.setCallEndDateTime(new Timestamp(endMillis));
        } catch (UnsupportedAudioFileException | IOException clipped) {
          // WAV 헤더를 읽다 실패하면, audioPlayTime과 callEndDateTime은 NULL 그대로 두어도 됩니다.
        }
      }

      // 8) callStatus가 여전히 비어 있으면(=JSON에도 없었으면) “OK” 로 기본값 채우기
      if (rec.getCallStatus() == null || rec.getCallStatus().isBlank()) {
        rec.setCallStatus("OK");
      }

      // 9) reg_date 칼럼은 NOT NULL이므로, 현재 시각으로 자동 세팅
      rec.setRegDate(Timestamp.valueOf(LocalDateTime.now()));

      // ────────────────────────────────────────────────────────────────────
      // 10) **중요**: ownerMemberSeq, branchSeq(회원 소속 지점) 자동 세팅
      // 파일명 또는 JSON에서 파싱된 rec.getNumber1() 값(내선번호)을 통해 회원을 조회
      if (rec.getNumber1() != null && !rec.getNumber1().isBlank()) {
        Optional<TmemberEntity> ownerOpt = memberRepository.findByNumber(rec.getNumber1().trim());
        if (ownerOpt.isPresent()) {
          TmemberEntity owner = ownerOpt.get();
          // (1) 이 녹취를 생성한 회원
          rec.setOwnerMemberSeq(owner.getMemberSeq());
          // (2) 해당 회원의 지점 번호(=branchSeq)
          rec.setBranchSeq(owner.getBranchSeq());
        }
        // 만약 내선번호에 해당하는 회원이 없으면, ownerMemberSeq와 branchSeq는 NULL 그대로 두어도 됩니다.
      }
      // ────────────────────────────────────────────────────────────────────

      // 11) DB에 INSERT
      trecordRepository.save(rec);
      insertedCount++;
    }

    return insertedCount;
  }

  /**
   * WAV 파일명과 같은 폴더에 동일한 이름의 .json 파일이 있는지 확인
   */
  private Path findMatchingJson(Path wavPath) {
    String name = wavPath.getFileName().toString();
    if (!name.toLowerCase().endsWith(WAV_EXTENSION)) {
      return null;
    }
    String baseName = name.substring(0, name.length() - WAV_EXTENSION.length());
    Path parent = wavPath.getParent();
    if (parent == null) return null;
    Path candidate = parent.resolve(baseName + JSON_EXTENSION);
    if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
      return candidate;
    }
    return null;
  }

  /**
   * 파일명(demo: "0331-I-01026886375_20250520171650.wav")에서
   *  • 시작 시간("20250520171650")
   *  • 내선(ext) "0331"
   *  • io "I"/"O" → "발신"/"수신"
   *  • 전화번호(other) "01026886375"
   * 등을 뽑아서 TrecordEntity 에 채워준다.
   *
   * 여기서 number1, number2, callStartDateTime, ioDiscdVal 은 절대로 NULL이 되지 않게 보장한다.
   */
  private void parseFromFilename(String filename,
      TrecordEntity rec,
      DateTimeFormatter formatter) {
    String nameOnly = filename;
    if (filename.toLowerCase().endsWith(WAV_EXTENSION)) {
      nameOnly = filename.substring(0, filename.length() - WAV_EXTENSION.length());
    }

    // "_" 기준으로 분리: ["0331-I-01026886375", "20250520171650"]
    String[] parts = nameOnly.split("_", 2);
    if (parts.length >= 2) {
      // (1) 뒤쪽 날짜("20250520171650") → callStartDateTime
      try {
        LocalDateTime ldt = LocalDateTime.parse(parts[1], formatter);
        rec.setCallStartDateTime(Timestamp.valueOf(ldt));
      } catch (Exception ignored) { }

      // (2) 앞쪽 "0331-I-01026886375" → ["0331", "I", "01026886375"]
      String[] tokens = parts[0].split("-", 3);
      if (tokens.length >= 1) {
        rec.setNumber1(tokens[0]);
      }
      if (tokens.length >= 2) {
        String io = tokens[1].trim().toUpperCase();
        if ("I".equals(io))        rec.setIoDiscdVal("발신");
        else if ("O".equals(io))   rec.setIoDiscdVal("수신");
        else                        rec.setIoDiscdVal(io);
      }
      if (tokens.length >= 3) {
        rec.setNumber2(tokens[2]);
      }
    }
  }

  /**
   * TrecordServiceImpl에서 호출할 스캔 진입점
   */
  @Transactional
  public void scanRecOnData() {
    try {
      int count = scanAndSaveNewRecords();
      // 원하면 로그로 삽입된 건수 확인
      System.out.println("scanRecOnData: inserted " + count + " new records");
    } catch (IOException e) {
      throw new RuntimeException("RecOnData 스캔 중 오류", e);
    }
  }


  /**
   * 매일 새벽 3시에 자동으로 RecOnData 스캔
   */
  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void scheduledScan() {
    try {
      int cnt = scanAndSaveNewRecords();
      log.info("자동 녹취 스캔 완료, 신규 등록: {}건", cnt);
    } catch (IOException e) {
      log.error("자동 녹취 스캔 실패", e);
    }
  }
}

