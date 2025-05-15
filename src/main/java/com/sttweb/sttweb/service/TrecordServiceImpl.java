package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.entity.TrecordEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.repository.TrecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class TrecordServiceImpl implements TrecordService {

  private final TrecordRepository repo;
  private final TmemberRepository memberRepo;

  private static final DateTimeFormatter DT_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private TrecordDto toDto(TrecordEntity e) {
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
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> findAll(Pageable pageable) {
    return repo.findAll(pageable).map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TrecordDto> searchByNumber(String number1, String number2, Pageable pageable) {
    if (number1 != null && number2 != null) {
      return repo.findByNumber1OrNumber2(number1, number2, pageable).map(this::toDto);
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
  public TrecordDto findById(Integer recordSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new IllegalArgumentException("녹취를 찾을 수 없습니다: " + recordSeq));
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
    return repo.findByNumber1OrNumber2(number, number, pageable).map(this::toDto);
  }

  // ───────────────────────────────────────────────────────────────

  /** 특정 사용자가 소유한 녹취(recordSeq)의 오디오 바이트를 반환 */
  @Override
  @Transactional(readOnly = true)
  public byte[] getAudioByIdAndUserSeq(Integer recordSeq, Integer targetUserSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new EntityNotFoundException("녹취를 찾을 수 없습니다: " + recordSeq));

    TmemberEntity member = memberRepo.findById(targetUserSeq)
        .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + targetUserSeq));
    if (!member.getNumber().equals(e.getNumber1())) {
      throw new SecurityException("오디오 청취 권한이 없습니다.");
    }

    try {
      Path path = Paths.get(e.getAudioFileDir());
      return Files.readAllBytes(path);
    } catch (Exception ex) {
      throw new RuntimeException("오디오 파일을 읽는 중 오류가 발생했습니다.", ex);
    }
  }

  /** 특정 사용자가 소유한 녹취(recordSeq)의 파일(Resource)를 반환 */
  @Override
  @Transactional(readOnly = true)
  public Resource getFileByIdAndUserSeq(Integer recordSeq, Integer targetUserSeq) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new EntityNotFoundException("녹취를 찾을 수 없습니다: " + recordSeq));

    TmemberEntity member = memberRepo.findById(targetUserSeq)
        .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + targetUserSeq));
    if (!member.getNumber().equals(e.getNumber1())) {
      throw new SecurityException("파일 다운로드 권한이 없습니다.");
    }

    try {
      Path path = Paths.get(e.getAudioFileDir());
      UrlResource resource = new UrlResource(path.toUri());
      if (!resource.exists() || !resource.isReadable()) {
        throw new RuntimeException("파일을 찾을 수 없거나 읽을 수 없습니다: " + path);
      }
      return resource;
    } catch (Exception ex) {
      throw new RuntimeException("파일 로드 중 오류가 발생했습니다.", ex);
    }
  }
}
