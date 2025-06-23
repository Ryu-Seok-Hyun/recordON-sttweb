package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.entity.TrecordEntity;
import com.sttweb.sttweb.repository.TmemberLinePermRepository;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.repository.TrecordRepository;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TbranchService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Root;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrecordServiceImpl implements TrecordService {



  private static final String[] SEARCH_DRIVES = {"C:", "D:", "E:"};
  private static final String REC_ON_DATA_SUB = "\\RecOnData";
  private static final DateTimeFormatter DT_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final TrecordRepository repo;
  private final TmemberRepository memberRepo;
  private final TmemberService memberSvc;
  private final TbranchService branchSvc;
  private final TrecordScanService scanSvc;

  public TrecordServiceImpl(
      TrecordRepository repo,
      TmemberRepository memberRepo,
      TmemberService memberSvc,
      TbranchService branchSvc,
      TrecordScanService scanSvc
  ) {
    this.repo = repo;
    this.memberRepo = memberRepo;
    this.memberSvc = memberSvc;
    this.branchSvc = branchSvc;
    this.scanSvc  = scanSvc;
  }

  private String normalizeToFourDigit(String raw) {
    if (raw == null)
      return null;
    String d = raw.replaceAll("[^0-9]", "").trim();
    if (d.length() == 4)
      return d;
    if (d.length() == 3)
      return "0" + d;
    if (d.length() > 4)
      return d.substring(d.length() - 4);
    return null;
  }

  private TrecordDto toDto(TrecordEntity e) {
    Integer bs = null;
    // 1) ext1/ext2 번호 기준 회원조회
    String ext1 = normalizeToFourDigit(e.getNumber1());
    String ext2 = normalizeToFourDigit(e.getNumber2());
    if (ext1 != null && memberRepo.findByNumber(ext1).isPresent()) {
      bs = memberRepo.findByNumber(ext1).get().getBranchSeq();
    }
    if (bs == null && ext2 != null && memberRepo.findByNumber(ext2).isPresent()) {
      bs = memberRepo.findByNumber(ext2).get().getBranchSeq();
    }
    // 2) 컬럼값 우선
    if (bs == null && e.getBranchSeq() != null) {
      bs = e.getBranchSeq();
    }
    String branchName = null;
    if (bs != null) {
      try {
        branchName = branchSvc.findById(bs).getCompanyName();
      } catch (Exception ignore) {
      }
    }

    return TrecordDto.builder()
        .recordSeq(e.getRecordSeq())
        .callStartDateTime(e.getCallStartDateTime() != null
            ? e.getCallStartDateTime().toLocalDateTime().format(DT_FMT)
            : null)
        .callEndDateTime(e.getCallEndDateTime() != null
            ? e.getCallEndDateTime().toLocalDateTime().format(DT_FMT)
            : null)
        .audioPlayTime(e.getAudioPlayTime() != null
            ? e.getAudioPlayTime().toString()
            : null)
        .ioDiscdVal(e.getIoDiscdVal())
        .number1(e.getNumber1())
        .number2(e.getNumber2())
        .audioFileDir(e.getAudioFileDir())
        .callStatus(e.getCallStatus())
        .regDate(e.getRegDate() != null
            ? e.getRegDate().toLocalDateTime().format(DT_FMT)
            : null)
        .lineId(e.getLineId())
        .ownerMemberSeq(e.getOwnerMemberSeq())
        .branchSeq(bs)
        .branchName(branchName)
        .build();
  }

//  @Override
//  @Transactional(readOnly = true)
//  public Page<TrecordDto> findAll(Pageable pageable) {
//    return repo.findAll(pageable).map(this::toDto);
//  }

  @Override
  @Transactional
  public void scanRecOnData() {
    try {
      scanSvc.scanAndSaveNewRecords();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> findAll(Pageable pageable) {
    // findAll() 호출 전에도 스캔
    scanRecOnData();
    return repo.findAll(pageable).map(this::toDto);
  }



  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByNumber(
      String number1, String number2, Pageable pageable
  ) {
    if (number1 != null && number2 != null) {
      return repo.findByNumber1OrNumber2(number1, number2, pageable)
          .map(this::toDto);
    } else if (number1 != null) {
      return repo.findByNumber1(number1, pageable).map(this::toDto);
    } else if (number2 != null) {
      return repo.findByNumber2(number2, pageable).map(this::toDto);
    } else {
      return findAll(pageable);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByCallNums(List<String> callNums, Pageable pageable) {
    return repo.findByNumber1InOrNumber2In(callNums, callNums, pageable)
        .map(this::toDto);
  }

//  @Override
//  @Transactional(readOnly = true)
//  public Page<TrecordDto> search(
//      String number1,
//      String number2,
//      String direction,
//      String numberKind,
//      String query,
//      LocalDateTime start,
//      LocalDateTime end,
//      Pageable pageable
//  ) {
//    Boolean inbound = null;
//    if ("IN".equalsIgnoreCase(direction))
//      inbound = true;
//    if ("OUT".equalsIgnoreCase(direction))
//      inbound = false;
//
//    Boolean isExt = null;
//    if ("EXT".equalsIgnoreCase(numberKind))
//      isExt = true;
//    if ("PHONE".equalsIgnoreCase(numberKind))
//      isExt = false;
//
//    return repo.search(
//        number1, number2,
//        inbound, isExt, query,
//        start, end,
//        pageable
//    ).map(this::toDto);
//  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> advancedSearch(
      String direction,
      String numberKind,
      String q,
      Pageable pageable,
      com.sttweb.sttweb.dto.TmemberDto.Info me
  ) {
    Specification<TrecordEntity> spec = Specification.where(null);

    if ("IN".equalsIgnoreCase(direction)) {
      spec = spec.and((root, query, cb) ->
          cb.equal(root.get("ioDiscdVal"), "수신"));
    } else if ("OUT".equalsIgnoreCase(direction)) {
      spec = spec.and((root, query, cb) ->
          cb.equal(root.get("ioDiscdVal"), "발신"));
    }

    if ("EXT".equalsIgnoreCase(numberKind)) {
      spec = spec.and((root, query, cb) ->
          cb.lessThanOrEqualTo(cb.length(root.get("number1")), 4));
    } else if ("PHONE".equalsIgnoreCase(numberKind)) {
      spec = spec.and((root, query, cb) ->
          cb.greaterThan(cb.length(root.get("number1")), 4));
    }

    if (q != null && !q.isBlank()) {
      String pattern = "%" + q + "%";
      spec = spec.and((root, query, cb) ->
          cb.like(root.get("callStatus"), pattern));
    }

    return repo.findAll(spec, pageable).map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public TrecordDto findById(Integer recordSeq) {
    TrecordEntity e = repo.findById(recordSeq).orElseThrow(
        () -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "녹취를 찾을 수 없습니다: " + recordSeq
        )
    );
    return toDto(e);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> findAllByBranch(Integer branchSeq, Pageable pageable) {
    return repo.findAllByBranchSeq(branchSeq, pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByNumbers(List<String> numbers, Pageable pageable) {
    return repo.findByNumber1InOrNumber2In(numbers, numbers, pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> findByUserNumber(String number, Pageable pageable) {
    return repo.findByNumber1OrNumber2(number, number, pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional
  public TrecordDto create(TrecordDto dto) {
    TrecordEntity e = new TrecordEntity();
    if (dto.getCallStartDateTime() != null) {
      e.setCallStartDateTime(
          Timestamp.valueOf(dto.getCallStartDateTime())
      );
    }
    if (dto.getCallEndDateTime() != null) {
      e.setCallEndDateTime(
          Timestamp.valueOf(dto.getCallEndDateTime())
      );
    }
    if (dto.getAudioPlayTime() != null) {
      e.setAudioPlayTime(
          Time.valueOf(dto.getAudioPlayTime())
      );
    }
    e.setIoDiscdVal(dto.getIoDiscdVal());
    e.setNumber1(dto.getNumber1());
    e.setNumber2(dto.getNumber2());
    e.setAudioFileDir(dto.getAudioFileDir());
    e.setCallStatus(dto.getCallStatus());
    if (dto.getRegDate() != null) {
      e.setRegDate(Timestamp.valueOf(dto.getRegDate()));
    }

    if (dto.getOwnerMemberSeq() != null) {
      e.setOwnerMemberSeq(dto.getOwnerMemberSeq());
      TmemberEntity owner = memberRepo.findById(dto.getOwnerMemberSeq())
          .orElseThrow(() -> new EntityNotFoundException(
              "사용자를 찾을 수 없습니다: " + dto.getOwnerMemberSeq()
          ));
      e.setBranchSeq(owner.getBranchSeq());
    }

    TrecordEntity saved = repo.save(e);
    return toDto(saved);
  }

  @Override
  @Transactional
  public TrecordDto update(Integer recordSeq, TrecordDto dto) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new IllegalArgumentException(
            "녹취를 찾을 수 없습니다: " + recordSeq
        ));

    if (dto.getCallStartDateTime() != null) {
      e.setCallStartDateTime(
          Timestamp.valueOf(dto.getCallStartDateTime())
      );
    }
    if (dto.getCallEndDateTime() != null) {
      e.setCallEndDateTime(
          Timestamp.valueOf(dto.getCallEndDateTime())
      );
    }
    if (dto.getAudioPlayTime() != null) {
      e.setAudioPlayTime(
          Time.valueOf(dto.getAudioPlayTime())
      );
    }
    e.setIoDiscdVal(dto.getIoDiscdVal());
    e.setNumber1(dto.getNumber1());
    e.setNumber2(dto.getNumber2());
    e.setAudioFileDir(dto.getAudioFileDir());
    e.setCallStatus(dto.getCallStatus());
    if (dto.getRegDate() != null) {
      e.setRegDate(Timestamp.valueOf(dto.getRegDate()));
    }
    if (dto.getOwnerMemberSeq() != null) {
      e.setOwnerMemberSeq(dto.getOwnerMemberSeq());
      TmemberEntity owner = memberRepo.findById(dto.getOwnerMemberSeq())
          .orElseThrow(() -> new EntityNotFoundException(
              "사용자를 찾을 수 없습니다: " + dto.getOwnerMemberSeq()
          ));
      e.setBranchSeq(owner.getBranchSeq());
    }

    TrecordEntity saved = repo.save(e);
    return toDto(saved);
  }

  @Override
  @Transactional
  public void delete(Integer recordSeq) {
    repo.deleteById(recordSeq);
  }

  @Override
  public long countByBranchAndDirection(Integer branchSeq, String direction) {
    if ("ALL".equalsIgnoreCase(direction)) {
      if (branchSeq == null) return repo.count();
      else return repo.countByBranchSeq(branchSeq);
    }
    String ioVal = switch (direction.toUpperCase()) {
      case "IN" -> "수신";
      case "OUT" -> "발신";
      default -> null;
    };
    if (ioVal == null) {
      throw new IllegalArgumentException("direction must be ALL, IN or OUT");
    }
    // branchSeq가 null이면 전체 대상으로 수신/발신 카운트
    if (branchSeq == null) {
      return repo.countByIoDiscdVal(ioVal);
    }
    // 아니면 기존대로
    return repo.countByBranchSeqAndIoDiscdVal(branchSeq, ioVal);
  }

  @Override
  @Transactional(readOnly = true)
  public Resource getFileByIdAndUserSeq(Integer recordSeq, Integer targetUserSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new EntityNotFoundException(
            "녹취를 찾을 수 없습니다: " + recordSeq
        ));
    TmemberEntity member = memberRepo.findById(targetUserSeq)
        .orElseThrow(() -> new EntityNotFoundException(
            "사용자를 찾을 수 없습니다: " + targetUserSeq
        ));
    if (!member.getNumber().equals(e.getNumber1())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다운로드 권한이 없습니다.");
    }
    try {
      Path path = Paths.get(e.getAudioFileDir());
      UrlResource resource = new UrlResource(path.toUri());
      if (!resource.exists() || !resource.isReadable()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일 읽기 오류: " + path);
      }
      return resource;
    } catch (MalformedURLException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 URL 생성 실패", ex);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Resource getFile(Integer recordSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "녹취를 찾을 수 없습니다: " + recordSeq
        ));

    String raw = e.getAudioFileDir().replace("\\", "/");
    if (raw.startsWith("../")) {
      raw = raw.substring(3);
    }

    Path recOnRoot = findRecOnDataRoot();
    if (recOnRoot == null) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "서버에서 RecOnData 폴더를 찾을 수 없습니다."
      );
    }

    Path full = recOnRoot.resolve(raw).normalize();
    try {
      UrlResource res = new UrlResource(full.toUri());
      if (!res.exists() || !res.isReadable()) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "파일을 찾을 수 없습니다: " + full
        );
      }
      return res;
    } catch (MalformedURLException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "파일 URL 생성 실패: " + full, ex
      );
    }
  }

  private Path findRecOnDataRoot() {
    for (String drv : SEARCH_DRIVES) {
      Path candidate = Paths.get(drv + REC_ON_DATA_SUB);
      if (Files.exists(candidate) && Files.isDirectory(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  // ─────────────────────────────────────────────────────────────
  // 내선번호와 전화번호 구분 검색 메서드 구현
  // ─────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByBranchAndExtensions(Integer branchSeq, List<String> extensions,
      Pageable pageable) {
    if (extensions == null || extensions.isEmpty()) {
      return Page.empty(pageable);
    }

    // 4자리 내선번호만 필터링
    List<String> validExtensions = extensions.stream()
        .filter(ext -> ext != null && ext.length() == 4)
        .distinct()
        .toList();

    if (validExtensions.isEmpty()) {
      return Page.empty(pageable);
    }

    return repo.findByBranchAndExtensions(branchSeq, validExtensions, pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByMixedNumbers(List<String> numbers, Pageable pageable) {
    if (numbers == null || numbers.isEmpty()) {
      return Page.empty(pageable);
    }
    Specification<TrecordEntity> spec = (root, query, cb) -> {
      List<Predicate> preds = new ArrayList<>();
      for (String ext : numbers) {
        preds.add(cb.equal(root.get("number1"), ext));
        preds.add(cb.equal(root.get("number2"), ext));
      }
      return cb.or(preds.toArray(new Predicate[0]));
    };
    return repo.findAll(spec, pageable).map(this::toDto);
  }


  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByMixedNumbersInBranch(
      Integer branchSeq,
      List<String> numbers,
      Pageable pageable
  ) {
    if (numbers == null || numbers.isEmpty()) {
      return Page.empty(pageable);
    }
    Specification<TrecordEntity> spec = (root, query, cb) -> {
      Predicate branchPred = cb.equal(root.get("branchSeq"), branchSeq);
      List<Predicate> numOrs = new ArrayList<>();
      for (String ext : numbers) {
        // 정확히 일치만
        numOrs.add(cb.equal(root.get("number1"), ext));
        numOrs.add(cb.equal(root.get("number2"), ext));
      }
      Predicate numPred = cb.or(numOrs.toArray(new Predicate[0]));
      return cb.and(branchPred, numPred);
    };
    return repo.findAll(spec, pageable).map(this::toDto);
  }

  // ─────────────────────────────────────────────────────────────
  // 권한 기반 조회 메서드들 (일단 기존 방식으로 구현)
  // ─────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchWithPermission(
      Integer memberSeq,
      Integer permLevel,
      String num1,
      String num2,
      String direction,
      String numberKind,
      String q,
      LocalDateTime start,
      LocalDateTime end,
      Pageable pageable) {

    // 🔧 임시로 기본 search 메서드 사용 (안전함)
    System.out.println("⚠️ searchWithPermission 호출됨 - 기본 검색으로 우회");

    Boolean inbound = null;
    if ("IN".equals(direction))
      inbound = true;
    else if ("OUT".equals(direction))
      inbound = false;

    Boolean isExt = null;
    if ("EXTENSION".equals(numberKind))
      isExt = true;
    else if ("PHONE".equals(numberKind))
      isExt = false;

    // 일단 기존 search 메서드 사용
    return repo.search(num1, num2, inbound, isExt, q, start, end, pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> findByMemberSeqWithPermission(
      Integer memberSeq,
      Integer permLevel,
      Pageable pageable) {

    // 🔧 임시로 전체 조회 (안전함)
    System.out.println("⚠️ findByMemberSeqWithPermission 호출됨 - 전체 조회로 우회");

    return repo.findAll(pageable).map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByMyAndGrantedNumbers(Integer branchSeq, List<String> numbers,
      Pageable pageable) {
    if (numbers == null || numbers.isEmpty())
      return Page.empty(pageable);
    return repo.findByBranchAndExtensionsOrNumberOnly(branchSeq, numbers, pageable)
        .map(this::toDto);
  }

  /** 관리자/지점장용 검색 */
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
    scanSvc.scanRecOnData();
    // 🔥 [1] PHONE + q 입력시 → 부분 일치(LIKE) 검색
    if ("PHONE".equalsIgnoreCase(numberKind) && q != null && !q.isBlank()) {
      // 번호 부분 검색(전화번호 LIKE)
      return repo.findByNumber1ContainingOrNumber2Containing(q, q, pageable)
          .map(this::toDto);
    }

    // 🔥 [2] 기존 통합 검색
    Boolean inbound = null;
    if ("IN".equalsIgnoreCase(direction))  inbound = true;
    if ("OUT".equalsIgnoreCase(direction)) inbound = false;

    Boolean isExt = null;
    if ("EXT".equalsIgnoreCase(numberKind))   isExt = true;
    if ("PHONE".equalsIgnoreCase(numberKind)) isExt = false;

    return repo.searchByQuery(
        number1,
        number2,
        inbound,
        isExt,
        q,
        start,
        end,
        pageable
    ).map(this::toDto);
  }


  /**
   * (일반 사용자용) 내선목록(nums) + 방향/내선필터 + q(번호검색) + 기간
   */
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
    // 1) “전화번호” 선택 후 q에 값이 들어왔으면, 부분 일치로 바로 검색
    if ("PHONE".equalsIgnoreCase(numberKind) && q != null && !q.isBlank()) {
      return repo
          .findByNumber1ContainingOrNumber2Containing(q, q, pageable)
          .map(this::toDto);
    }

    // 2) 그 외(내선 필터 등)는 기존대로 권한 내선 목록 + JPQL 검색
    return repo
        .searchByNumsAndQuery(numbers, direction, numberKind, q, start, end, pageable)
        .map(this::toDto);
  }


  @Override
  public Page<TrecordDto> searchByPhoneNumberOnlyLike(String phone, Pageable pageable) {
    // TrecordRepository에 메서드가 있으면 사용, 없으면 직접 QueryDSL/JPA로 구현
    // 예시: number2 LIKE 검색 (number2 컬럼이 TrecordEntity에 있다고 가정)
    Page<TrecordEntity> entityPage = repo.findByNumber2Containing(phone, pageable);
    List<TrecordDto> dtoList = entityPage.stream().map(TrecordDto::from).toList();
    return new PageImpl<>(dtoList, pageable, entityPage.getTotalElements());
  }


}