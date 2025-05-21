package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.entity.TrecordEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.repository.TrecordRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrecordServiceImpl implements TrecordService {

  @Value("${app.audio.base-dir:\\\\192.168.55.180\\RecOnData}")
  private String audioBaseDir;

  private final TrecordRepository repo;
  private final TmemberRepository memberRepo;
  private final TmemberService memberSvc;

  private static final DateTimeFormatter DT_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public TrecordServiceImpl(TrecordRepository repo,
      TmemberRepository memberRepo,
      TmemberService memberSvc) {
    this.repo = repo;
    this.memberRepo = memberRepo;
    this.memberSvc = memberSvc;
  }

  /** Entity → DTO 변환 헬퍼 */
  private TrecordDto toDto(TrecordEntity e) {
    TrecordDto.TrecordDtoBuilder b = TrecordDto.builder()
        .recordSeq(e.getRecordSeq())
        .callStartDateTime(e.getCallStartDateTime() != null
            ? e.getCallStartDateTime().toLocalDateTime().format(DT_FMT)
            : null)
        .callEndDateTime(e.getCallEndDateTime() != null
            ? e.getCallEndDateTime().toLocalDateTime().format(DT_FMT)
            : null)
        .audioPlayTime(e.getAudioPlayTime() != null
            ? e.getAudioPlayTime().toString() : null)
        .ioDiscdVal(e.getIoDiscdVal())
        .number1(e.getNumber1())
        .number2(e.getNumber2())
        .audioFileDir(e.getAudioFileDir())
        .callStatus(e.getCallStatus())
        .regDate(e.getRegDate() != null
            ? e.getRegDate().toLocalDateTime().format(DT_FMT)
            : null);

    // 소유자(memberSeq) 및 branchSeq 추가
    try {
      Integer ownerSeq = memberSvc.getMemberSeqByNumber(e.getNumber1());
      if (ownerSeq != null) {
        TmemberEntity owner = memberRepo.findById(ownerSeq).orElse(null);
        if (owner != null) {
          b.ownerMemberSeq(ownerSeq)
              .branchSeq(owner.getBranchSeq());
        }
      }
    } catch (Exception ignored) {
    }

    return b.build();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> findAll(Pageable pageable) {
    return repo.findAll(pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByNumber(String number1,
      String number2,
      Pageable pageable) {
    if (number1 != null && number2 != null) {
      return repo.findByNumber1OrNumber2(number1, number2, pageable)
          .map(this::toDto);
    } else if (number1 != null) {
      return repo.findByNumber1(number1, pageable)
          .map(this::toDto);
    } else if (number2 != null) {
      return repo.findByNumber2(number2, pageable)
          .map(this::toDto);
    } else {
      return findAll(pageable);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> search(String number1,
      String number2,
      String direction,
      String numberKind,
      String query,
      Pageable pageable) {
    // direction: ALL/IN/OUT → inbound flag
    Boolean inbound = null;
    if ("IN".equalsIgnoreCase(direction))  inbound = true;
    if ("OUT".equalsIgnoreCase(direction)) inbound = false;

    // numberKind: ALL/PHONE/EXT → isExt flag
    Boolean isExt = null;
    if ("EXT".equalsIgnoreCase(numberKind))  isExt = true;
    if ("PHONE".equalsIgnoreCase(numberKind)) isExt = false;

    return repo.search(number1, number2, inbound, isExt, query, pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> advancedSearch(
      String direction,
      String numberKind,
      String q,
      Pageable pageable,
      Info me
  ) {
    // 1) 기본 전체 조회 (페이징)
    Page<TrecordDto> base = repo.findAll(pageable).map(this::toDto);

    // 2) in-memory 필터링 후 리스트로 수집
    List<TrecordDto> filtered = base.stream()
        .filter(dto -> {
          // 방향 필터: 'I' = IN, 'O' = OUT
          if ("IN".equalsIgnoreCase(direction)  && !"I".equals(dto.getIoDiscdVal()))  return false;
          if ("OUT".equalsIgnoreCase(direction) && !"O".equals(dto.getIoDiscdVal())) return false;

          // 내선(EXT)/전화번호(PHONE) 필터
          boolean isExtNumber = dto.getNumber1() != null && dto.getNumber1().length() <= 4;
          if ("EXT".equalsIgnoreCase(numberKind)  && !isExtNumber) return false;
          if ("PHONE".equalsIgnoreCase(numberKind) &&  isExtNumber) return false;

          // 통화내용(q) 키워드 필터
          if (q != null && !q.isBlank()) {
            return dto.getCallStatus() != null && dto.getCallStatus().contains(q);
          }
          return true;
        })
        .collect(Collectors.toList());

    // 3) PageImpl으로 감싸서 반환
    return new PageImpl<>(filtered, pageable, filtered.size());
  }

  @Override
  @Transactional(readOnly = true)
  public TrecordDto findById(Integer recordSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "녹취를 찾을 수 없습니다: " + recordSeq));
    return toDto(e);
  }

  @Override
  @Transactional
  public TrecordDto create(TrecordDto dto) {
    TrecordEntity e = new TrecordEntity();
    if (dto.getCallStartDateTime() != null) {
      e.setCallStartDateTime(Timestamp.valueOf(dto.getCallStartDateTime()));
    }
    if (dto.getCallEndDateTime() != null) {
      e.setCallEndDateTime(Timestamp.valueOf(dto.getCallEndDateTime()));
    }
    if (dto.getAudioPlayTime() != null) {
      e.setAudioPlayTime(Time.valueOf(dto.getAudioPlayTime()));
    }
    e.setIoDiscdVal(dto.getIoDiscdVal());
    e.setNumber1(dto.getNumber1());
    e.setNumber2(dto.getNumber2());
    e.setAudioFileDir(dto.getAudioFileDir());
    e.setCallStatus(dto.getCallStatus());
    if (dto.getRegDate() != null) {
      e.setRegDate(Timestamp.valueOf(dto.getRegDate()));
    }
    TrecordEntity saved = repo.save(e);
    return toDto(saved);
  }

  @Override
  @Transactional
  public TrecordDto update(Integer recordSeq, TrecordDto dto) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new IllegalArgumentException("녹취를 찾을 수 없습니다: " + recordSeq));
    if (dto.getCallStartDateTime() != null) {
      e.setCallStartDateTime(Timestamp.valueOf(dto.getCallStartDateTime()));
    }
    if (dto.getCallEndDateTime() != null) {
      e.setCallEndDateTime(Timestamp.valueOf(dto.getCallEndDateTime()));
    }
    if (dto.getAudioPlayTime() != null) {
      e.setAudioPlayTime(Time.valueOf(dto.getAudioPlayTime()));
    }
    e.setIoDiscdVal(dto.getIoDiscdVal());
    e.setNumber1(dto.getNumber1());
    e.setNumber2(dto.getNumber2());
    e.setAudioFileDir(dto.getAudioFileDir());
    e.setCallStatus(dto.getCallStatus());
    if (dto.getRegDate() != null) {
      e.setRegDate(Timestamp.valueOf(dto.getRegDate()));
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
  @Transactional(readOnly = true)
  public Page<TrecordDto> findByUserNumber(String number, Pageable pageable) {
    return repo.findByNumber1OrNumber2(number, number, pageable)
        .map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Resource getFileByIdAndUserSeq(Integer recordSeq, Integer targetUserSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new EntityNotFoundException("녹취를 찾을 수 없습니다: " + recordSeq));
    TmemberEntity member = memberRepo.findById(targetUserSeq)
        .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + targetUserSeq));
    if (!member.getNumber().equals(e.getNumber1())) {
      throw new SecurityException("다운로드 권한이 없습니다.");
    }
    try {
      Path path = Paths.get(e.getAudioFileDir());
      UrlResource resource = new UrlResource(path.toUri());
      if (!resource.exists() || !resource.isReadable()) {
        throw new RuntimeException("파일 읽기 오류: " + path);
      }
      return resource;
    } catch (Exception ex) {
      throw new RuntimeException("파일 로드 오류", ex);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Resource getFile(Integer recordSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "녹취를 찾을 수 없습니다: " + recordSeq));

    // DB에 저장된 상대경로 예: "20240125\\0334-O-950_20240125103148.wav"
    String raw = e.getAudioFileDir().replace("\\", "/");
    Path base = Paths.get(audioBaseDir);
    Path full = base.resolve(raw).normalize();

    try {
      UrlResource res = new UrlResource(full.toUri());
      if (!res.exists() || !res.isReadable()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            "파일을 찾을 수 없습니다: " + full);
      }
      return res;
    } catch (MalformedURLException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "파일 URL 생성 실패: " + full, ex);
    }
  }
}
