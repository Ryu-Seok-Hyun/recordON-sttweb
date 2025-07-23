package com.sttweb.sttweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.entity.TrecordEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.repository.TrecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
  private final TmemberRepository memberRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String[] DRIVES         = { "C:", "D:", "E:" };
  private static final String   ROOT_SUBPATH   = "\\RecOnData";
  private static final String   WAV_EXTENSION  = ".wav";
  private static final String   JSON_EXTENSION = ".json";

  private Path findRecOnDataRoot() {
    for (String drive : DRIVES) {
      Path candidate = Paths.get(drive + ROOT_SUBPATH);
      if (Files.exists(candidate) && Files.isDirectory(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private Path findMatchingJson(Path wavPath) {
    String name = wavPath.getFileName().toString();
    if (!name.toLowerCase().endsWith(WAV_EXTENSION)) {
      return null;
    }
    String base = name.substring(0, name.length() - WAV_EXTENSION.length());
    Path parent = wavPath.getParent();
    if (parent == null) return null;
    Path candidate = parent.resolve(base + JSON_EXTENSION);
    return (Files.exists(candidate) && Files.isRegularFile(candidate)) ? candidate : null;
  }

  private void parseFromFilename(String filename, TrecordEntity rec, DateTimeFormatter fmt) {
    String nameOnly = filename;
    if (filename.toLowerCase().endsWith(WAV_EXTENSION)) {
      nameOnly = filename.substring(0, filename.length() - WAV_EXTENSION.length());
    }
    String[] parts = nameOnly.split("_", 2);
    if (parts.length < 2) {
      return;
    }
    try {
      LocalDateTime ldt = LocalDateTime.parse(parts[1], fmt);
      rec.setCallStartDateTime(Timestamp.valueOf(ldt));
    } catch (Exception ignored) {}

    String[] toks = parts[0].split("-", 3);
    if (toks.length >= 1) {
      rec.setNumber1(toks[0]);
    }
    if (toks.length >= 2) {
      String io = toks[1].trim().toUpperCase();
      rec.setIoDiscdVal("I".equals(io) ? "발신" : "O".equals(io) ? "수신" : io);
    }
    if (toks.length >= 3) {
      rec.setNumber2(toks[2]);
    }
  }

  private void applyJsonMetadata(Path jsonPath, TrecordEntity rec, DateTimeFormatter fmt) {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(jsonPath.toFile()), Charset.forName("MS949"))
    )) {
      JsonNode root       = objectMapper.readTree(reader);
      JsonNode paramsNode = root.path("params");
      JsonNode userdata   = paramsNode.path("userdata");

      if (userdata.has("issueDate") && !userdata.get("issueDate").asText().isBlank()) {
        try {
          LocalDate datePart = LocalDate.parse(userdata.get("issueDate").asText().trim(), fmt);
          rec.setCallStartDateTime(Timestamp.valueOf(datePart.atStartOfDay()));
        } catch (Exception ignored) {}
      } else if (root.has("callStartDateTime")) {
        rec.setCallStartDateTime(Timestamp.valueOf(LocalDateTime.parse(root.get("callStartDateTime").asText())));
      }

      if (root.has("callEndDateTime")) {
        rec.setCallEndDateTime(Timestamp.valueOf(LocalDateTime.parse(root.get("callEndDateTime").asText())));
      }

      if (root.has("playTime")) {
        rec.setAudioPlayTime(Time.valueOf(root.get("playTime").asText()));
      }

      if (userdata.has("IOvalue") && !userdata.get("IOvalue").asText().isBlank()) {
        String io = userdata.get("IOvalue").asText().trim().toUpperCase();
        rec.setIoDiscdVal("I".equals(io) ? "발신" : "O".equals(io) ? "수신" : rec.getIoDiscdVal());
      } else if (root.has("io")) {
        String io = root.get("io").asText().trim().toUpperCase();
        rec.setIoDiscdVal("I".equals(io) ? "발신" : "O".equals(io) ? "수신" : rec.getIoDiscdVal());
      }

      if (userdata.has("speaker1")) {
        rec.setNumber1(userdata.get("speaker1").asText().trim());
      } else if (root.has("ext")) {
        rec.setNumber1(root.get("ext").asText().trim());
      }

      if (userdata.has("speaker2")) {
        rec.setNumber2(userdata.get("speaker2").asText().trim());
      } else if (root.has("other")) {
        rec.setNumber2(root.get("other").asText().trim());
      }

      if (userdata.has("status")) {
        rec.setCallStatus(userdata.get("status").asText().trim());
      } else if (root.has("callStatus")) {
        rec.setCallStatus(root.get("callStatus").asText().trim());
      }

    } catch (IOException e) {
      log.warn("JSON 메타 적용 오류: {}", jsonPath, e);
    }
  }

  private void applyWavHeader(Path wavFullPath, TrecordEntity rec) {
    if (rec.getCallStartDateTime() == null || rec.getCallEndDateTime() != null) {
      return;
    }
    try {
      File wavFile = wavFullPath.toFile();
      AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile);
      AudioFormat format = ais.getFormat();
      long frames = ais.getFrameLength();
      double frameRate = format.getFrameRate();
      double secs = frames / frameRate;
      ais.close();

      long secondsTotal = Math.round(secs);
      rec.setAudioPlayTime(Time.valueOf(LocalTime.ofSecondOfDay(secondsTotal)));

      long startMillis = rec.getCallStartDateTime().getTime();
      rec.setCallEndDateTime(new Timestamp(startMillis + (long)(secs * 1000)));
    } catch (UnsupportedAudioFileException | IOException clipped) {
      // WAV 헤더 읽기 실패 시 무시
    }
  }

  private void applyOwnerAndBranch(TrecordEntity rec) {
    if (rec.getNumber1() == null || rec.getNumber1().isBlank()) {
      return;
    }
    memberRepository.findByNumber(rec.getNumber1().trim())
        .ifPresent(owner -> {
          rec.setOwnerMemberSeq(owner.getMemberSeq());
          rec.setBranchSeq(owner.getBranchSeq());
        });
  }


  @Retryable(
      value = {
          DeadlockLoserDataAccessException.class,
          DataIntegrityViolationException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 500)
  )
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int scanAndSaveNewRecords() throws IOException {
    Path rootDir = findRecOnDataRoot();
    if (rootDir == null) {
      return 0;
    }

    List<TrecordEntity> existingList = trecordRepository.findAll();
    Set<String> existingRelPaths = new HashSet<>();
    for (TrecordEntity e : existingList) {
      existingRelPaths.add(e.getAudioFileDir());
    }

    List<Path> allWavFiles = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(rootDir)) {
      walk.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(WAV_EXTENSION))
          .forEach(allWavFiles::add);
    }

    int insertedCount = 0;
    DateTimeFormatter fileNameDtFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    DateTimeFormatter issueDateFmt = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    for (Path wavFullPath : allWavFiles) {
      Path relative = rootDir.relativize(wavFullPath);
      String audioFileDir = "../" + relative.toString().replace("\\", "/");

      if (existingRelPaths.contains(audioFileDir)) {
        continue;
      }

      TrecordEntity rec = new TrecordEntity();
      rec.setAudioFileDir(audioFileDir);
      parseFromFilename(wavFullPath.getFileName().toString(), rec, fileNameDtFmt);

      Path jsonCandidate = findMatchingJson(wavFullPath);
      if (jsonCandidate != null) {
        applyJsonMetadata(jsonCandidate, rec, issueDateFmt);
      }

      applyWavHeader(wavFullPath, rec);

      if (rec.getCallStatus() == null || rec.getCallStatus().isBlank()) {
        rec.setCallStatus("OK");
      }

      rec.setRegDate(Timestamp.valueOf(LocalDateTime.now()));
      applyOwnerAndBranch(rec);

      trecordRepository.save(rec);
      insertedCount++;
    }

    return insertedCount;
  }

  // Deadlock 재시도 실패 시
  @Recover
  public int recoverOnDeadlock(DeadlockLoserDataAccessException ex) {
    log.error("🔁 Deadlock 재시도 실패 — 계속 진행합니다.", ex);
    return 0;
  }

  // 중복 키 제약 위반 시
  @Recover
  public int recoverOnDuplicate(DataIntegrityViolationException ex) {
    log.warn("🔁 중복 데이터 스킵: {}", ex.getMessage());
    return 0;
  }

  // 그 외 모든 예외 (꼭 필요!!)
  @Recover
  public int recoverOnAny(Exception ex) {
    log.error("🔁 기타 예외 재시도 실패: {}", ex.getMessage(), ex);
    return 0;
  }

  @Transactional
  public void scanRecOnData() {
    try {
      int count = scanAndSaveNewRecords();
      System.out.println("scanRecOnData: inserted " + count + " new records");
    } catch (Exception e) {
      throw new RuntimeException("RecOnData 스캔 중 오류", e);
    }
  }

  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void scheduledScan() {
    try {
      int cnt = scanAndSaveNewRecords();
      log.info("자동 녹취 스캔 완료, 신규 등록: {}건", cnt);
    } catch (Exception e) {
      log.error("자동 녹취 스캔 실패", e);
    }
  }
}
