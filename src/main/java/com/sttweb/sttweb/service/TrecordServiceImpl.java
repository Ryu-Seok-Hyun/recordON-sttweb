package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.entity.TrecordEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.repository.TrecordRepository;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TbranchService;
import jakarta.persistence.EntityNotFoundException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrecordServiceImpl implements TrecordService {

  private static final String[] SEARCH_DRIVES   = { "C:", "D:", "E:" };
  private static final String   REC_ON_DATA_SUB = "\\RecOnData";
  private static final DateTimeFormatter DT_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final TrecordRepository repo;
  private final TmemberRepository memberRepo;
  private final TmemberService    memberSvc;
  private final TbranchService    branchSvc;

  public TrecordServiceImpl(
      TrecordRepository repo,
      TmemberRepository memberRepo,
      TmemberService memberSvc,
      TbranchService branchSvc
  ) {
    this.repo       = repo;
    this.memberRepo = memberRepo;
    this.memberSvc  = memberSvc;
    this.branchSvc  = branchSvc;
  }

  /**
   * Entity → DTO 변환 헬퍼
   * - e.getBranchSeq()가 null인 레거시는 number1/number2로 회원 조회 후 대체
   * - 매칭되는 지점 없으면 branchName == null
   */
  private TrecordDto toDto(TrecordEntity e) {
    Integer bs = e.getBranchSeq();
    if (bs == null) {
      // legacy: branchSeq == null 이면 number1 기준 회원 검색
      TmemberEntity m = memberRepo.findByNumber(e.getNumber1())
          .orElseGet(() -> memberRepo.findByNumber(e.getNumber2()).orElse(null));
      if (m != null) {
        bs = m.getBranchSeq();
      }
    }

    String branchName = null;
    if (bs != null) {
      try {
        branchName = branchSvc.findById(bs).getCompanyName();
      } catch (Exception ignore) {
        // 매칭 실패 시 null 유지
      }
    }

    return TrecordDto.builder()
        .recordSeq(e.getRecordSeq())
        .callStartDateTime(
            e.getCallStartDateTime() != null
                ? e.getCallStartDateTime().toLocalDateTime().format(DT_FMT)
                : null
        )
        .callEndDateTime(
            e.getCallEndDateTime() != null
                ? e.getCallEndDateTime().toLocalDateTime().format(DT_FMT)
                : null
        )
        .audioPlayTime(
            e.getAudioPlayTime() != null
                ? e.getAudioPlayTime().toString()
                : null
        )
        .ioDiscdVal(e.getIoDiscdVal())
        .number1(e.getNumber1())
        .number2(e.getNumber2())
        .audioFileDir(e.getAudioFileDir())
        .callStatus(e.getCallStatus())
        .regDate(
            e.getRegDate() != null
                ? e.getRegDate().toLocalDateTime().format(DT_FMT)
                : null
        )
        .ownerMemberSeq(e.getOwnerMemberSeq())
        .branchSeq(bs)
        .branchName(branchName)
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> findAll(Pageable pageable) {
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
  public Page<TrecordDto> search(
      String number1,
      String number2,
      String direction,
      String numberKind,
      String query,
      LocalDateTime start,
      LocalDateTime end,
      Pageable pageable
  ) {
    Boolean inbound = null;
    if ("IN".equalsIgnoreCase(direction))  inbound = true;
    if ("OUT".equalsIgnoreCase(direction)) inbound = false;

    Boolean isExt = null;
    if ("EXT".equalsIgnoreCase(numberKind))  isExt = true;
    if ("PHONE".equalsIgnoreCase(numberKind)) isExt = false;

    return repo.search(
        number1, number2,
        inbound, isExt, query,
        start, end,
        pageable
    ).map(this::toDto);
  }

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
      return repo.countByBranchSeq(branchSeq);
    }
    String ioVal = switch(direction.toUpperCase()) {
      case "IN"  -> "수신";
      case "OUT" -> "발신";
      default    -> null;
    };
    if (ioVal == null) {
      throw new IllegalArgumentException("direction must be ALL, IN or OUT");
    }
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
}
