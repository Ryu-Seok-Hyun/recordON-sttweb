package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.entity.TrecordEntity;
import com.sttweb.sttweb.entity.TrecordTelListEntity;
import com.sttweb.sttweb.exception.ResourceNotFoundException;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.repository.TrecordRepository;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.nio.file.*;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TrecordServiceImpl implements TrecordService {

  private static final Logger log = LoggerFactory.getLogger(TrecordServiceImpl.class);
  private static final String[] SEARCH_DRIVES = {"C:", "D:", "E:"};
  private static final String REC_ON_DATA_SUB = "\\RecOnData";
  private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final RecordingIngestService ingest;      // ★ 신규 주입
  private final TrecordRepository repo;
  private final TmemberRepository memberRepo;
  private final TmemberService memberSvc;
  private final TbranchService branchSvc;
  private final TrecordScanService scanSvc;
  private final TrecordTelListRepository telRepo;


  public TrecordServiceImpl(
      TrecordRepository repo,
      TmemberRepository memberRepo,
      TmemberService memberSvc,
      TbranchService branchSvc,
      TrecordScanService scanSvc,
      TrecordTelListRepository telRepo,
      RecordingIngestService ingest
  ) {
    this.repo       = repo;
    this.memberRepo = memberRepo;
    this.memberSvc  = memberSvc;
    this.branchSvc  = branchSvc;
    this.scanSvc    = scanSvc;
    this.telRepo    = telRepo;
    this.ingest     = ingest; // ★ 주입
  }

  private Map<String, TmemberEntity> numberToMemberMap(List<TrecordEntity> entities) {
    Set<String> allNumbers = entities.stream()
        .flatMap(e -> Stream.of(e.getNumber1(), e.getNumber2()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    if (allNumbers.isEmpty()) return Collections.emptyMap();
    List<TmemberEntity> members = memberRepo.findByNumberIn(allNumbers);
    return members.stream().collect(Collectors.toMap(TmemberEntity::getNumber, m -> m));
  }

  private Map<Integer, String> branchSeqToNameMap(List<TrecordEntity> entities) {
    Set<Integer> bSeqs = entities.stream()
        .map(TrecordEntity::getBranchSeq)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    if (bSeqs.isEmpty()) return Collections.emptyMap();
    Map<Integer, String> result = new HashMap<>();
    for (Integer seq : bSeqs) {
      try { result.put(seq, branchSvc.findById(seq).getCompanyName()); } catch (Exception ignore) {}
    }
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByPhoneEnding(String phoneEnding, Pageable pageable) {
    if (!StringUtils.hasText(phoneEnding)) return Page.empty(pageable);

    Specification<TrecordEntity> spec = (root, query, cb) ->
        cb.like(root.get("number2"), "%" + phoneEnding); // ← 끝자리 일치 (뒤에 % 없음)

    return repo.findAll(spec, pageable).map(this::toDto);
  }



  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByMixedNumbers(
      List<String> numbers,
      String direction,
      String numberKind,
      String q,
      LocalDateTime start,
      LocalDateTime end,
      Pageable pageable
  ) {
    if (numbers == null || numbers.isEmpty()) return Page.empty(pageable);

    Specification<TrecordEntity> spec = (root, query, cb) -> {
      List<Predicate> ps = new ArrayList<>();

      // 접근 가능 번호 제한
      List<Predicate> ors = new ArrayList<>();
      for (String n : numbers) {
        ors.add(cb.equal(root.get("number1"), n));
        ors.add(cb.equal(root.get("number2"), n));
      }
      ps.add(cb.or(ors.toArray(new Predicate[0])));

      // 0초 통화 제외
      ps.add(cb.or(cb.isNull(root.get("audioPlayTime")), cb.notEqual(root.get("audioPlayTime"), Time.valueOf("00:00:00"))));

      // 기간
      if (start != null) ps.add(cb.greaterThanOrEqualTo(root.get("callStartDateTime"), Timestamp.valueOf(start)));
      if (end   != null) ps.add(cb.lessThanOrEqualTo(root.get("callStartDateTime"),   Timestamp.valueOf(end)));

      // 수/발신
      if ("IN".equalsIgnoreCase(direction))      ps.add(cb.equal(root.get("ioDiscdVal"), "수신"));
      else if ("OUT".equalsIgnoreCase(direction)) ps.add(cb.equal(root.get("ioDiscdVal"), "발신"));

      // q 해석
      String digits = StringUtils.hasText(q) ? q.replaceAll("[^0-9]", "") : null;
      String ext    = normalizeToFourDigit(digits);

      if ("EXT".equalsIgnoreCase(numberKind)) {
        if (ext != null) {
          ps.add(cb.equal(root.get("number1"), ext));
        } else {
          ps.add(cb.lessThanOrEqualTo(cb.length(root.get("number1")), 4));
        }
      } else if ("PHONE".equalsIgnoreCase(numberKind)) {
        if (StringUtils.hasText(digits)) {
          ps.add(cb.like(root.get("number2"), "%" + digits));
        } else {
          ps.add(cb.greaterThan(cb.length(root.get("number1")), 4));
        }
      } else {
        if (StringUtils.hasText(q)) {
          Predicate byExt   = (ext != null)
              ? cb.or(cb.equal(root.get("number1"), ext), cb.equal(root.get("number2"), ext))
              : cb.disjunction();
          Predicate byPhone = (StringUtils.hasText(digits))
              ? cb.like(root.get("number2"), "%" + digits)
              : cb.disjunction();
          Predicate byText  = cb.like(root.get("callStatus"), "%" + q + "%");
          ps.add(cb.or(byExt, byPhone, byText));
        }
      }

      query.orderBy(cb.desc(root.get("callStartDateTime")));
      return cb.and(ps.toArray(new Predicate[0]));
    };

    return repo.findAll(spec, pageable).map(this::toDto);
  }





  private TrecordDto toDto(TrecordEntity e, Map<String, TmemberEntity> numberMap, Map<Integer, String> branchNameMap) {
    Integer bs = null;
    String ext1 = normalizeToFourDigit(e.getNumber1());
    String ext2 = normalizeToFourDigit(e.getNumber2());
    if (ext1 != null && numberMap.containsKey(ext1)) bs = numberMap.get(ext1).getBranchSeq();
    if (bs == null && ext2 != null && numberMap.containsKey(ext2)) bs = numberMap.get(ext2).getBranchSeq();
    if (bs == null && e.getBranchSeq() != null) bs = e.getBranchSeq();
    String branchName = bs != null ? branchNameMap.get(bs) : null;

    return TrecordDto.builder()
        .recordSeq(e.getRecordSeq())
        .callStartDateTime(e.getCallStartDateTime() != null ? e.getCallStartDateTime().toLocalDateTime().format(DT_FMT) : null)
        .callEndDateTime(e.getCallEndDateTime() != null ? e.getCallEndDateTime().toLocalDateTime().format(DT_FMT) : null)
        .audioPlayTime(e.getAudioPlayTime() != null ? e.getAudioPlayTime().toString() : null)
        .ioDiscdVal(e.getIoDiscdVal())
        .number1(e.getNumber1())
        .number2(e.getNumber2())
        .audioFileDir(e.getAudioFileDir())
        .callStatus(e.getCallStatus())
        .regDate(e.getRegDate() != null ? e.getRegDate().toLocalDateTime().format(DT_FMT) : null)
        .lineId(e.getLineId())
        .ownerMemberSeq(e.getOwnerMemberSeq())
        .branchSeq(bs)
        .branchName(branchName)
        .build();
  }

  private String normalizeToFourDigit(String raw) {
    if (raw == null) return null;
    String d = raw.replaceAll("[^0-9]", "").trim();
    if (d.length() == 4) return d;
    if (d.length() == 3) return "0" + d;
    if (d.length() > 4)  return d.substring(d.length() - 4);
    return null;
  }

  private TrecordDto toDto(TrecordEntity e) {
    Integer bs = null;
    String ext1 = normalizeToFourDigit(e.getNumber1());
    String ext2 = normalizeToFourDigit(e.getNumber2());
    if (ext1 != null && memberRepo.findByNumber(ext1).isPresent()) bs = memberRepo.findByNumber(ext1).get().getBranchSeq();
    if (bs == null && ext2 != null && memberRepo.findByNumber(ext2).isPresent()) bs = memberRepo.findByNumber(ext2).get().getBranchSeq();
    if (bs == null && e.getBranchSeq() != null) bs = e.getBranchSeq();
    String branchName = null;
    if (bs != null) { try { branchName = branchSvc.findById(bs).getCompanyName(); } catch (Exception ignore) {} }

    return TrecordDto.builder()
        .recordSeq(e.getRecordSeq())
        .callStartDateTime(e.getCallStartDateTime() != null ? e.getCallStartDateTime().toLocalDateTime().format(DT_FMT) : null)
        .callEndDateTime(e.getCallEndDateTime() != null ? e.getCallEndDateTime().toLocalDateTime().format(DT_FMT) : null)
        .audioPlayTime(e.getAudioPlayTime() != null ? e.getAudioPlayTime().toString() : null)
        .ioDiscdVal(e.getIoDiscdVal())
        .number1(e.getNumber1())
        .number2(e.getNumber2())
        .audioFileDir(e.getAudioFileDir())
        .callStatus(e.getCallStatus())
        .regDate(e.getRegDate() != null ? e.getRegDate().toLocalDateTime().format(DT_FMT) : null)
        .lineId(e.getLineId())
        .ownerMemberSeq(e.getOwnerMemberSeq())
        .branchSeq(bs)
        .branchName(branchName)
        .build();
  }

  @Override @Transactional
  public void scanRecOnData() { scanSvc.scanRecOnData(); }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> findAll(Pageable pageable) {
    Page<TrecordEntity> page = repo.findAll(pageable);
    Map<String, TmemberEntity> numberMap = numberToMemberMap(page.getContent());
    Map<Integer, String> branchNameMap = branchSeqToNameMap(page.getContent());
    return page.map(e -> toDto(e, numberMap, branchNameMap));
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByNumber(String number1, String number2, Pageable pageable) {
    Page<TrecordEntity> page;
    if (number1 != null && number2 != null) page = repo.findByNumber1OrNumber2(number1, number2, pageable);
    else if (number1 != null) page = repo.findByNumber1(number1, pageable);
    else if (number2 != null) page = repo.findByNumber2(number2, pageable);
    else page = repo.findAll(pageable);
    Map<String, TmemberEntity> numberMap = numberToMemberMap(page.getContent());
    Map<Integer, String> branchNameMap = branchSeqToNameMap(page.getContent());
    return page.map(e -> toDto(e, numberMap, branchNameMap));
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByCallNums(List<String> callNums, Pageable pageable) {
    return repo.findByNumber1InOrNumber2In(callNums, callNums, pageable).map(this::toDto);
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> advancedSearch(String direction, String numberKind, String q, Pageable pageable, com.sttweb.sttweb.dto.TmemberDto.Info me) {
    Specification<TrecordEntity> spec = Specification.where(null);
    if ("IN".equalsIgnoreCase(direction))  spec = spec.and((r,qr,cb)->cb.equal(r.get("ioDiscdVal"),"수신"));
    else if ("OUT".equalsIgnoreCase(direction)) spec = spec.and((r,qr,cb)->cb.equal(r.get("ioDiscdVal"),"발신"));
    if ("EXT".equalsIgnoreCase(numberKind)) spec = spec.and((r,qr,cb)->cb.lessThanOrEqualTo(cb.length(r.get("number1")),4));
    else if ("PHONE".equalsIgnoreCase(numberKind)) {
      if (StringUtils.hasText(q)) spec = spec.and((r,qr,cb)->cb.like(r.get("number2"),"%"+q));
      else spec = spec.and((r,qr,cb)->cb.greaterThan(cb.length(r.get("number1")),4));
    }
    if (StringUtils.hasText(q) && !"PHONE".equalsIgnoreCase(numberKind)) {
      String pattern = "%" + q + "%";
      spec = spec.and((r,qr,cb)->cb.like(r.get("callStatus"), pattern));
    }
    return repo.findAll(spec, pageable).map(this::toDto);
  }

  @Override @Transactional(readOnly = true)
  public TrecordDto findById(Integer recordSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "녹취를 찾을 수 없습니다: " + recordSeq));
    return toDto(e);
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> findAllByBranch(Integer branchSeq, Pageable pageable) {
    Page<TrecordEntity> page = repo.findAllByBranchSeq(branchSeq, pageable);
    Map<String, TmemberEntity> numberMap = numberToMemberMap(page.getContent());
    Map<Integer, String> branchNameMap = branchSeqToNameMap(page.getContent());
    return page.map(e -> toDto(e, numberMap, branchNameMap));
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByNumbers(List<String> numbers, Pageable pageable) {
    Page<TrecordEntity> page = repo.findByNumber1InOrNumber2In(numbers, numbers, pageable);
    Map<String, TmemberEntity> numberMap = numberToMemberMap(page.getContent());
    Map<Integer, String> branchNameMap = branchSeqToNameMap(page.getContent());
    return page.map(e -> toDto(e, numberMap, branchNameMap));
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> findByUserNumber(String number, Pageable pageable) {
    return repo.findByNumber1OrNumber2(number, number, pageable).map(this::toDto);
  }

  @Override @Transactional
  public TrecordDto create(TrecordDto dto) {
    TrecordEntity e = new TrecordEntity();
    if (dto.getCallStartDateTime()!=null) e.setCallStartDateTime(Timestamp.valueOf(dto.getCallStartDateTime()));
    if (dto.getCallEndDateTime()!=null)   e.setCallEndDateTime(Timestamp.valueOf(dto.getCallEndDateTime()));
    if (dto.getAudioPlayTime()!=null)     e.setAudioPlayTime(Time.valueOf(dto.getAudioPlayTime()));
    e.setIoDiscdVal(dto.getIoDiscdVal());
    e.setNumber1(dto.getNumber1());
    e.setNumber2(dto.getNumber2());
    e.setAudioFileDir(dto.getAudioFileDir());
    e.setCallStatus(dto.getCallStatus());
    if (dto.getRegDate()!=null) e.setRegDate(Timestamp.valueOf(dto.getRegDate()));
    if (dto.getOwnerMemberSeq()!=null) {
      e.setOwnerMemberSeq(dto.getOwnerMemberSeq());
      TmemberEntity owner = memberRepo.findById(dto.getOwnerMemberSeq())
          .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + dto.getOwnerMemberSeq()));
      e.setBranchSeq(owner.getBranchSeq());
    }
    return toDto(repo.save(e));
  }

  @Override @Transactional
  public TrecordDto update(Integer recordSeq, TrecordDto dto) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new IllegalArgumentException("녹취를 찾을 수 없습니다: " + recordSeq));
    if (dto.getCallStartDateTime()!=null) e.setCallStartDateTime(Timestamp.valueOf(dto.getCallStartDateTime()));
    if (dto.getCallEndDateTime()!=null)   e.setCallEndDateTime(Timestamp.valueOf(dto.getCallEndDateTime()));
    if (dto.getAudioPlayTime()!=null)     e.setAudioPlayTime(Time.valueOf(dto.getAudioPlayTime()));
    e.setIoDiscdVal(dto.getIoDiscdVal());
    e.setNumber1(dto.getNumber1());
    e.setNumber2(dto.getNumber2());
    e.setAudioFileDir(dto.getAudioFileDir());
    e.setCallStatus(dto.getCallStatus());
    if (dto.getRegDate()!=null) e.setRegDate(Timestamp.valueOf(dto.getRegDate()));
    if (dto.getOwnerMemberSeq()!=null) {
      e.setOwnerMemberSeq(dto.getOwnerMemberSeq());
      TmemberEntity owner = memberRepo.findById(dto.getOwnerMemberSeq())
          .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + dto.getOwnerMemberSeq()));
      e.setBranchSeq(owner.getBranchSeq());
    }
    return toDto(repo.save(e));
  }

  @Override @Transactional
  public void delete(Integer recordSeq) { repo.deleteById(recordSeq); }

  @Override
  public long countByBranchAndDirection(Integer branchSeq, String direction) {
    if ("ALL".equalsIgnoreCase(direction)) return (branchSeq == null) ? repo.count() : repo.countByBranchSeq(branchSeq);
    String ioVal = switch (direction.toUpperCase()) { case "IN" -> "수신"; case "OUT" -> "발신"; default -> null; };
    if (ioVal == null) throw new IllegalArgumentException("direction must be ALL, IN or OUT");
    return (branchSeq == null) ? repo.countByIoDiscdVal(ioVal) : repo.countByBranchSeqAndIoDiscdVal(branchSeq, ioVal);
  }

  @Override @Transactional(readOnly = true)
  public Resource getFileByIdAndUserSeq(Integer recordSeq, Integer targetUserSeq) {
    TrecordEntity e = repo.findById(recordSeq).orElseThrow(() -> new EntityNotFoundException("녹취를 찾을 수 없습니다: " + recordSeq));
    TmemberEntity member = memberRepo.findById(targetUserSeq).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + targetUserSeq));
    if (!member.getNumber().equals(e.getNumber1())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다운로드 권한이 없습니다. 관리자에게 문의하세요.");
    try {
      Path path = Paths.get(e.getAudioFileDir());
      UrlResource resource = new UrlResource(path.toUri());
      if (!resource.exists() || !resource.isReadable()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일 읽기 오류: " + path);
      return resource;
    } catch (MalformedURLException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 URL 생성 실패", ex);
    }
  }

  /**
   * HQ 분기는 Controller에서 처리. 여기서는 로컬 전용.
   * *_enc.mp3 → .mp3 사전 복호화 및 DB 경로 갱신 지원.
   */
  @Override
  @Transactional
  public Resource getFile(Integer recordSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new EntityNotFoundException("녹취를 찾을 수 없습니다: " + recordSeq));

    String raw = e.getAudioFileDir().replace("\\", "/");
    if (raw.startsWith("../")) raw = raw.substring(3);

    for (String drive : SEARCH_DRIVES) {
      Path candidate = Paths.get(drive + REC_ON_DATA_SUB, raw).normalize();
      try {
        if (Files.exists(candidate) && Files.isReadable(candidate)) {
          Path normalized = ingest.normalize(candidate); // ★ enc.mp3 자동 복호화
          if (!normalized.getFileName().toString().equals(candidate.getFileName().toString())) {
            String newRel = toRelativePath(normalized);
            repo.updateAudioPath(e.getRecordSeq(), newRel);
            e.setAudioFileDir(newRel);
          }
          return new UrlResource(normalized.toUri());
        }
      } catch (Exception ignore) {}
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다: " + raw);
  }

  @Override
  @Transactional
  public Resource getLocalFile(Integer id) {
    TrecordEntity record = repo.findById(id).orElseThrow(() ->
        new ResourceNotFoundException("녹취 레코드를 찾을 수 없습니다: " + id));

    String dateFolder = record.getCallStartDateTime()
        .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    String rawPath = record.getAudioFileDir().replace("\\", "/");
    String fileName = Paths.get(rawPath).getFileName().toString();

    for (String drive : List.of("C:", "D:", "E:")) {
      Path path = Paths.get(drive, "RecOnData", dateFolder, fileName).normalize();
      try {
        if (Files.exists(path) && Files.isReadable(path)) {
          Path normalized = ingest.normalize(path); // ★ enc.mp3 자동 복호화
          if (!normalized.getFileName().toString().equals(path.getFileName().toString())) {
            String newRel = toRelativePath(normalized);
            repo.updateAudioPath(record.getRecordSeq(), newRel);
            record.setAudioFileDir(newRel);
          }
          return new FileSystemResource(normalized);
        }
      } catch (Exception ignore) {}
    }
    throw new ResourceNotFoundException("로컬에서 파일을 찾을 수 없습니다: " + dateFolder + "/" + fileName);
  }

  private Path findRecOnDataRoot() {
    for (String drv : SEARCH_DRIVES) {
      Path candidate = Paths.get(drv + REC_ON_DATA_SUB);
      if (Files.exists(candidate) && Files.isDirectory(candidate)) return candidate;
    }
    return null;
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByBranchAndExtensions(Integer branchSeq, List<String> extensions, Pageable pageable) {
    if (extensions == null || extensions.isEmpty()) return Page.empty(pageable);
    List<String> valid = extensions.stream().filter(x -> x != null && x.length() == 4).distinct().toList();
    if (valid.isEmpty()) return Page.empty(pageable);
    return repo.findByBranchAndExtensions(branchSeq, valid, pageable).map(this::toDto);
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByMixedNumbers(List<String> numbers, Pageable pageable) {
    if (numbers == null || numbers.isEmpty()) return Page.empty(pageable);
    Specification<TrecordEntity> spec = (root, q, cb) -> {
      List<Predicate> ps = new ArrayList<>();
      for (String n : numbers) { ps.add(cb.equal(root.get("number1"), n)); ps.add(cb.equal(root.get("number2"), n)); }
      return cb.or(ps.toArray(new Predicate[0]));
    };
    return repo.findAll(spec, pageable).map(this::toDto);
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByMixedNumbersInBranch(Integer branchSeq, List<String> numbers, Pageable pageable) {
    if (numbers == null || numbers.isEmpty()) return Page.empty(pageable);
    Specification<TrecordEntity> spec = (root, q, cb) -> {
      var branchPred = cb.equal(root.get("branchSeq"), branchSeq);
      List<Predicate> ps = new ArrayList<>();
      for (String n : numbers) { ps.add(cb.equal(root.get("number1"), n)); ps.add(cb.equal(root.get("number2"), n)); }
      return cb.and(branchPred, cb.or(ps.toArray(new Predicate[0])));
    };
    return repo.findAll(spec, pageable).map(this::toDto);
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchWithPermission(Integer memberSeq, Integer permLevel, String num1, String num2,
      String direction, String numberKind, String q,
      LocalDateTime start, LocalDateTime end, Pageable pageable) {
    Boolean inbound = "IN".equals(direction) ? true : "OUT".equals(direction) ? false : null;
    Boolean isExt   = "EXTENSION".equals(numberKind) ? true : "PHONE".equals(numberKind) ? false : null;
    return repo.search(num1, num2, inbound, isExt, q, start, end, pageable).map(this::toDto);
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> findByMemberSeqWithPermission(Integer memberSeq, Integer permLevel, Pageable pageable) {
    return repo.findAll(pageable).map(this::toDto);
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByMyAndGrantedNumbers(Integer branchSeq, List<String> numbers, Pageable pageable) {
    if (numbers == null || numbers.isEmpty()) return Page.empty(pageable);
    return repo.findByBranchAndExtensionsOrNumberOnly(branchSeq, numbers, pageable).map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> search(
      String number1,
      String number2,
      String direction,
      String numberKind,
      String q,
      LocalDateTime start,
      LocalDateTime end,
      Pageable pageable
  ) {
    Specification<TrecordEntity> spec = (root, query, cb) -> {
      List<Predicate> ps = new ArrayList<>();

      // 0초 통화 제외
      ps.add(cb.or(cb.isNull(root.get("audioPlayTime")), cb.notEqual(root.get("audioPlayTime"), Time.valueOf("00:00:00"))));

      // 기간
      if (start != null) ps.add(cb.greaterThanOrEqualTo(root.get("callStartDateTime"), Timestamp.valueOf(start)));
      if (end   != null) ps.add(cb.lessThanOrEqualTo(root.get("callStartDateTime"),   Timestamp.valueOf(end)));

      // 수/발신
      if ("IN".equalsIgnoreCase(direction))      ps.add(cb.equal(root.get("ioDiscdVal"), "수신"));
      else if ("OUT".equalsIgnoreCase(direction)) ps.add(cb.equal(root.get("ioDiscdVal"), "발신"));

      // q 해석
      String digits = StringUtils.hasText(q) ? q.replaceAll("[^0-9]", "") : null;
      String ext    = normalizeToFourDigit(digits);

      if ("EXT".equalsIgnoreCase(numberKind)) {
        // 내선 모드: q가 있으면 정확히 해당 내선만, 없으면 내선(4자리) 전체
        if (ext != null) {
          ps.add(cb.equal(root.get("number1"), ext));
        } else {
          ps.add(cb.lessThanOrEqualTo(cb.length(root.get("number1")), 4));
        }
      } else if ("PHONE".equalsIgnoreCase(numberKind)) {
        // 전화번호 모드: q가 있으면 number2 뒤자리 like, 없으면 외부번호(=내선 아님)만
        if (StringUtils.hasText(digits)) {
          ps.add(cb.like(root.get("number2"), "%" + digits));
        } else {
          ps.add(cb.greaterThan(cb.length(root.get("number1")), 4));
        }
      } else {
        // ALL 모드: q가 있으면 [내선 일치 OR 전화번호 뒤자리 like OR 상태문구 like] 중 하나라도 매치
        if (StringUtils.hasText(q)) {
          Predicate byExt   = (ext != null)
              ? cb.or(cb.equal(root.get("number1"), ext), cb.equal(root.get("number2"), ext))
              : cb.disjunction();
          Predicate byPhone = (StringUtils.hasText(digits))
              ? cb.like(root.get("number2"), "%" + digits)
              : cb.disjunction();
          Predicate byText  = cb.like(root.get("callStatus"), "%" + q + "%");
          ps.add(cb.or(byExt, byPhone, byText));
        }
      }

      query.orderBy(cb.desc(root.get("callStartDateTime")));
      return cb.and(ps.toArray(new Predicate[0]));
    };

    Page<TrecordEntity> page = repo.findAll(spec, pageable);
    Map<String, TmemberEntity> numberMap   = numberToMemberMap(page.getContent());
    Map<Integer, String>       branchNames = branchSeqToNameMap(page.getContent());
    return page.map(e -> toDto(e, numberMap, branchNames));
  }


  @Override
  public Page<TrecordDto> searchByPhoneNumberOnlyLike(String phone, Pageable pageable) {
    return repo.findByNumber1ContainingOrNumber2Containing(phone, phone, pageable).map(this::toDto);
  }

  @Cacheable(cacheNames = "telList")
  public Map<Integer, String> loadLineIdToCallNum() {
    return telRepo.findAll().stream().collect(Collectors.toMap(TrecordTelListEntity::getId, TrecordTelListEntity::getCallNum));
  }

  public Map<String, Long> getInboundOutboundCount(LocalDateTime start, LocalDateTime end) {
    return repo.countByDirectionGrouped(start, end).stream().collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByAudioFileBasenames(Collection<String> basenames, Pageable pageable) {
    if (basenames == null || basenames.isEmpty()) return Page.empty(pageable);
    Page<TrecordEntity> page = repo.findByAudioBasenames(basenames, pageable);
    Map<String, TmemberEntity> numberMap = numberToMemberMap(page.getContent());
    Map<Integer, String> branchNameMap = branchSeqToNameMap(page.getContent());
    return page.map(e -> toDto(e, numberMap, branchNameMap));
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByAudioFileNames(List<String> fileNames, Pageable pageable) {
    if (fileNames == null || fileNames.isEmpty()) return Page.empty(pageable);
    List<String> bases = fileNames.stream()
        .filter(Objects::nonNull).map(String::trim).map(s -> s.replace('\\','/'))
        .map(s -> { int i = s.lastIndexOf('/'); return (i >= 0 ? s.substring(i + 1) : s); })
        .map(String::toLowerCase).distinct().toList();

    Specification<TrecordEntity> spec = (root, query, cb) -> {
      var path = cb.lower(root.get("audioFileDir"));
      List<Predicate> orPreds = new ArrayList<>();
      for (String b : bases) {
        String noExt = b.replaceAll("\\.wav$", "");
        orPreds.add(cb.like(path, "%" + noExt + "%"));
        String plus = noExt.replace("-i-", "+").replace("-o-", "+");
        orPreds.add(cb.like(path, "%" + plus + "%"));
        int us = noExt.lastIndexOf('_');
        if (us > 0 && us < noExt.length() - 1) {
          String ts = noExt.substring(us + 1);
          orPreds.add(cb.like(path, "%_" + ts + ".wav"));
        }
      }
      Predicate timeOk = cb.or(cb.isNull(root.get("audioPlayTime")), cb.notEqual(root.get("audioPlayTime"), java.sql.Time.valueOf("00:00:00")));
      return cb.and(timeOk, cb.or(orPreds.toArray(new Predicate[0])));
    };

    Page<TrecordEntity> page = repo.findAll(spec, pageable);
    Map<String, TmemberEntity> numberMap = numberToMemberMap(page.getContent());
    Map<Integer, String> branchNameMap = branchSeqToNameMap(page.getContent());
    return page.map(e -> toDto(e, numberMap, branchNameMap));
  }

  @Override @Transactional(readOnly = true)
  public Page<TrecordDto> searchByAudioBasenamesWithFilters(Collection<String> basenames, String direction, String numberKind,
      String number, LocalDateTime start, LocalDateTime end, Pageable pageable) {
    if (basenames == null || basenames.isEmpty()) return Page.empty(pageable);
    String digits = (number == null) ? null : number.replaceAll("[^0-9]", "");
    String ext = normalizeToFourDigit(digits);
    String phoneEnd = digits;

    Page<TrecordEntity> page = repo.findByBasenamesAndFilters(basenames, direction, numberKind, number, ext, phoneEnd, start, end, pageable);
    Map<String, TmemberEntity> numberMap = numberToMemberMap(page.getContent());
    Map<Integer, String> branchNameMap = branchSeqToNameMap(page.getContent());
    return page.map(e -> toDto(e, numberMap, branchNameMap));
  }

  @Override @Transactional(readOnly = true)
  public long countByFilters(String direction, String numberKind, String number, LocalDateTime start, LocalDateTime end) {
    String digits = (number == null) ? null : number.replaceAll("[^0-9]", "");
    String ext = normalizeToFourDigit(digits);
    String phoneEnd = digits;
    return repo.countByFilters(direction, numberKind, number, ext, phoneEnd, start, end);
  }

  // ====== enc.mp3 사전 복호화/마이그레이션 유틸 ======

  @Override
  public Path resolveAbsolutePath(String relativePath) {
    String rel = Objects.toString(relativePath, "").replace("\\", "/");
    if (rel.startsWith("../")) rel = rel.substring(3);
    Path root = findRecOnDataRoot();
    if (root == null) throw new IllegalStateException("RecOnData 루트를 찾을 수 없습니다.");
    return root.resolve(rel).normalize();
  }

  @Override
  public String toRelativePath(Path absolutePath) {
    Path root = findRecOnDataRoot();
    if (root == null) throw new IllegalStateException("RecOnData 루트를 찾을 수 없습니다.");
    Path rel = root.toAbsolutePath().normalize().relativize(absolutePath.toAbsolutePath().normalize());
    return "../" + rel.toString().replace("\\", "/");
  }

  @Override @Transactional
  public void updateAudioPath(Integer recordSeq, String newRelativePath) {
    repo.updateAudioPath(recordSeq, newRelativePath);
  }

  @Override @Transactional(readOnly = true)
  public List<TrecordDto> findLegacyEncMp3() {
    return repo.findByAudioFileSuffix("_enc.mp3").stream().map(this::toDto).toList();
  }
}
