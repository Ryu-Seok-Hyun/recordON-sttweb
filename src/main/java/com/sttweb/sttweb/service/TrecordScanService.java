package com.sttweb.sttweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final RecordingIngestService ingest;

  private static final String[] DRIVES       = { "C:", "D:", "E:" };
  private static final String   ROOT_SUBPATH = "\\RecOnData";
  private static final String   JSON_EXTENSION = ".json";

  private static final String[] AUDIO_SUFFIXES = { "_enc.mp3", ".mp3", ".wav", ".aes" };

  private static boolean isAudioFile(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    for (String s : AUDIO_SUFFIXES) if (n.endsWith(s)) return true;
    return false;
  }
  private static String stripAudioSuffix(String name) {
    String n = name;
    for (String s : AUDIO_SUFFIXES) {
      if (n.toLowerCase(Locale.ROOT).endsWith(s)) return n.substring(0, n.length() - s.length());
    }
    return n;
  }

  private Path findRecOnDataRoot() {
    for (String drive : DRIVES) {
      Path candidate = Paths.get(drive + ROOT_SUBPATH);
      if (Files.exists(candidate) && Files.isDirectory(candidate)) return candidate;
    }
    return null;
  }

  private Path findMatchingJson(Path audioPath) {
    Path parent = audioPath.getParent(); if (parent == null) return null;
    String base = stripAudioSuffix(audioPath.getFileName().toString());
    Path candidate = parent.resolve(base + JSON_EXTENSION);
    return (Files.exists(candidate) && Files.isRegularFile(candidate)) ? candidate : null;
  }

  private void parseFromFilename(String filename, TrecordEntity rec, DateTimeFormatter fmt) {
    String nameOnly = stripAudioSuffix(filename);
    String[] parts = nameOnly.split("_", 2);
    if (parts.length >= 2) {
      try { rec.setCallStartDateTime(Timestamp.valueOf(LocalDateTime.parse(parts[1], fmt))); } catch (Exception ignore) {}
    }
    String[] toks = parts[0].split("-", 3);
    if (toks.length >= 1) rec.setNumber1(toks[0]);
    if (toks.length >= 2) {
      String io = toks[1].trim().toUpperCase(Locale.ROOT);
      rec.setIoDiscdVal("I".equals(io) ? "발신" : "O".equals(io) ? "수신" : io);
    }
    if (toks.length >= 3) rec.setNumber2(toks[2]);
  }

  private void applyJsonMetadata(Path jsonPath, TrecordEntity rec, DateTimeFormatter fmt) {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(jsonPath.toFile()), Charset.forName("MS949")))) {
      JsonNode root = objectMapper.readTree(reader);
      JsonNode paramsNode = root.path("params");
      JsonNode userdata = paramsNode.path("userdata");

      if (userdata.has("issueDate") && !userdata.get("issueDate").asText().isBlank()) {
        try {
          LocalDate datePart = LocalDate.parse(userdata.get("issueDate").asText().trim(), fmt);
          rec.setCallStartDateTime(Timestamp.valueOf(datePart.atStartOfDay()));
        } catch (Exception ignored) {}
      } else if (root.has("callStartDateTime")) {
        rec.setCallStartDateTime(Timestamp.valueOf(LocalDateTime.parse(root.get("callStartDateTime").asText())));
      }

      if (root.has("callEndDateTime")) rec.setCallEndDateTime(Timestamp.valueOf(LocalDateTime.parse(root.get("callEndDateTime").asText())));
      if (root.has("playTime"))       rec.setAudioPlayTime(Time.valueOf(root.get("playTime").asText()));

      if (userdata.has("IOvalue") && !userdata.get("IOvalue").asText().isBlank()) {
        String io = userdata.get("IOvalue").asText().trim().toUpperCase(Locale.ROOT);
        rec.setIoDiscdVal("I".equals(io) ? "발신" : "O".equals(io) ? "수신" : rec.getIoDiscdVal());
      } else if (root.has("io")) {
        String io = root.get("io").asText().trim().toUpperCase(Locale.ROOT);
        rec.setIoDiscdVal("I".equals(io) ? "발신" : "O".equals(io) ? "수신" : rec.getIoDiscdVal());
      }

      if (userdata.has("speaker1")) rec.setNumber1(userdata.get("speaker1").asText().trim());
      else if (root.has("ext"))     rec.setNumber1(root.get("ext").asText().trim());

      if (userdata.has("speaker2")) rec.setNumber2(userdata.get("speaker2").asText().trim());
      else if (root.has("other"))   rec.setNumber2(root.get("other").asText().trim());

      if (userdata.has("status")) rec.setCallStatus(userdata.get("status").asText().trim());
      else if (root.has("callStatus")) rec.setCallStatus(root.get("callStatus").asText().trim());
    } catch (IOException e) {
      log.warn("JSON 메타 적용 오류: {}", jsonPath, e);
    }
  }

  /** MP3 SPI의 duration 속성(μs) 우선, 없으면 프레임 기반 보조 계산. */
  /** MP3 길이 채우기: 파일기반 우선 -> 스트림기반 보완 */
  private void applyAudioHeader(Path audioFullPath, TrecordEntity rec) {
    Long micros = null;

    // 0) enc.mp3면 가능하면 실제 mp3 파일로 풀어 파일기반 파싱
    Path target = audioFullPath;
    try {
      String name = audioFullPath.getFileName().toString().toLowerCase(Locale.ROOT);
      if (name.endsWith("_enc.mp3")) {
        try {
          // 디스크에 .mp3 생성(이미 존재하면 교체) — normalize 내부에서 처리
          target = ingest.normalize(audioFullPath);
        } catch (Exception e) {
          log.warn("enc.mp3 복호화 실패, 스트림 파싱으로 대체: {}", audioFullPath, e);
        }
      }
    } catch (Exception ignore) {}

    // 1) 파일기반: 가장 안정적으로 duration 제공
    if (micros == null) {
      try {
        AudioFileFormat aff = AudioSystem.getAudioFileFormat(target.toFile());
        Object d = aff.properties().get("duration");
        if (d instanceof Long l) micros = l;
        else if (d != null) micros = Long.parseLong(d.toString());
      } catch (Exception ignore) {}
    }

    // 2) 스트림기반 보완 (duration 속성 시도 -> 안되면 프레임계산)
    if (micros == null) {
      try (InputStream base = ingest.openPossiblyDecrypted(audioFullPath);
          BufferedInputStream bin = new BufferedInputStream(base)) {

        try {
          AudioFileFormat aff = AudioSystem.getAudioFileFormat(bin);
          Object d = aff.properties().get("duration");
          if (d instanceof Long l) micros = l;
          else if (d != null) micros = Long.parseLong(d.toString());
        } catch (Exception ignore) {}

        if (micros == null) {
          try (BufferedInputStream bin2 =
              new BufferedInputStream(ingest.openPossiblyDecrypted(audioFullPath));
              AudioInputStream ais = AudioSystem.getAudioInputStream(bin2)) {
            long frames = ais.getFrameLength();
            float rate  = ais.getFormat().getFrameRate();
            if (frames > 0 && rate > 0) micros = (long)((frames / rate) * 1_000_000L);
          }
        }
      } catch (Exception e) {
        log.debug("오디오 길이 파싱 실패: {}", audioFullPath, e);
      }
    }

    if (micros != null && micros > 0) {
      long totalSec = Math.max(1L, Math.round(micros / 1_000_000.0));
      rec.setAudioPlayTime(Time.valueOf(LocalTime.ofSecondOfDay(totalSec)));
      if (rec.getCallStartDateTime() != null && rec.getCallEndDateTime() == null) {
        rec.setCallEndDateTime(new Timestamp(rec.getCallStartDateTime().getTime() + totalSec * 1000));
      }
    }
  }


  private void applyOwnerAndBranch(TrecordEntity rec) {
    if (rec.getNumber1() == null || rec.getNumber1().isBlank()) return;
    memberRepository.findByNumber(rec.getNumber1().trim())
        .ifPresent(owner -> { rec.setOwnerMemberSeq(owner.getMemberSeq()); rec.setBranchSeq(owner.getBranchSeq()); });
  }

  @Retryable(value = { DeadlockLoserDataAccessException.class, DataIntegrityViolationException.class },
      maxAttempts = 3, backoff = @Backoff(delay = 500))
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int scanAndSaveNewRecords() throws IOException {
    Path rootDir = findRecOnDataRoot();
    if (rootDir == null)
      return 0;

    Set<String> existingRelPaths = new HashSet<>();
    trecordRepository.findAll().forEach(e -> existingRelPaths.add(e.getAudioFileDir()));

    List<Path> allAudioFiles = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(rootDir)) {
      walk.filter(p -> Files.isRegularFile(p) && isAudioFile(p.getFileName().toString()))
          .forEach(allAudioFiles::add);
    }

    int inserted = 0;
    DateTimeFormatter fileNameDtFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    DateTimeFormatter issueDateFmt = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    for (Path audioFullPath : allAudioFiles) {
      Path relative = rootDir.relativize(audioFullPath);
      String audioFileDir = "../" + relative.toString().replace("\\", "/");

      // ── 기존 레코드가 있으면 '길이/종료시각' 보정 업데이트 ──
      var existed = trecordRepository.findFirstByAudioFileDir(audioFileDir);
      if (existed.isPresent()) {
        TrecordEntity old = existed.get();
        boolean needSave = false;

        // 시작시각이 비어있으면 파일명에서 보정
        if (old.getCallStartDateTime() == null) {
          parseFromFilename(audioFullPath.getFileName().toString(), old, fileNameDtFmt);
          needSave = true;
        }

        // 길이 없거나 00:00:00 이면 헤더로 채움
        if (old.getAudioPlayTime() == null
            || old.getAudioPlayTime().toString().equals("00:00:00")) {
          applyAudioHeader(audioFullPath, old); // 여기서 audioPlayTime 채움
          needSave = true;
        }

        // 종료시각 비어있고, 시작+길이 있으면 계산
        if (old.getCallEndDateTime() == null
            && old.getCallStartDateTime() != null
            && old.getAudioPlayTime() != null) {
          long sec = old.getAudioPlayTime().toLocalTime().toSecondOfDay();
          old.setCallEndDateTime(new Timestamp(old.getCallStartDateTime().getTime() + sec * 1000));
          needSave = true;
        }

        if (needSave)
          trecordRepository.save(old);
        continue; // 신규 삽입 로직은 건너뜀
      }

      // ── 신규 레코드 삽입 ──
      TrecordEntity rec = new TrecordEntity();
      rec.setAudioFileDir(audioFileDir);
      parseFromFilename(audioFullPath.getFileName().toString(), rec, fileNameDtFmt);

      Path jsonCandidate = findMatchingJson(audioFullPath);
      if (jsonCandidate != null)
        applyJsonMetadata(jsonCandidate, rec, issueDateFmt);

      applyAudioHeader(audioFullPath, rec); // audioPlayTime, callEndDateTime 세팅

      if (rec.getCallStatus() == null || rec.getCallStatus().isBlank())
        rec.setCallStatus("OK");
      rec.setRegDate(Timestamp.valueOf(LocalDateTime.now()));
      applyOwnerAndBranch(rec);

      trecordRepository.save(rec);
      inserted++;
    }
    return inserted;
  }


  /** 기존 레코드의 비어있는 길이/종료시각 백필 */
  @Transactional
  public int backfillMissingDurations() {
    Path root = findRecOnDataRoot(); if (root == null) return 0;
    int n = 0;
    for (var rec : trecordRepository.findWithoutDuration()) {
      String rel = Optional.ofNullable(rec.getAudioFileDir()).orElse("").replace("\\","/");
      if (rel.startsWith("../")) rel = rel.substring(3);
      Path abs = root.resolve(rel).normalize();
      try { applyAudioHeader(abs, rec); trecordRepository.save(rec); n++; }
      catch (Exception e) { log.warn("백필 실패: {}", abs, e); }
    }
    return n;
  }

  @Recover public int recoverOnDeadlock(DeadlockLoserDataAccessException ex){ log.error("Deadlock 실패", ex); return 0; }
  @Recover public int recoverOnDuplicate(DataIntegrityViolationException ex){ log.warn("중복 스킵: {}", ex.getMessage()); return 0; }
  @Recover public int recoverOnAny(Exception ex){ log.error("기타 예외", ex); return 0; }

  @Transactional public void scanRecOnData(){ try { scanAndSaveNewRecords(); } catch (Exception e){ throw new RuntimeException("RecOnData 스캔 오류", e);} }


}
