package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.entity.TrecordEntity;
import com.sttweb.sttweb.repository.TrecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrecordServiceImpl implements TrecordService {

  private final TrecordRepository repo;

  // 날짜/시간 포맷터
  private static final DateTimeFormatter DT_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private TrecordDto toDto(TrecordEntity e) {
    return TrecordDto.builder()
        .recordSeq(e.getRecordSeq())
        // Timestamp → String
        .callStartDateTime(e.getCallStartDateTime() != null
            ? e.getCallStartDateTime().toLocalDateTime().format(DT_FMT)
            : null)
        .callEndDateTime(e.getCallEndDateTime() != null
            ? e.getCallEndDateTime().toLocalDateTime().format(DT_FMT)
            : null)
        // Time → String
        .audioPlayTime(e.getAudioPlayTime() != null
            ? e.getAudioPlayTime().toString()
            : null)
        .ioDiscdVal(e.getIoDiscdVal())
        .number1(e.getNumber1())
        .number2(e.getNumber2())
        .audioFileDir(e.getAudioFileDir())
        .callStatus(e.getCallStatus())
        // Timestamp → String
        .regDate(e.getRegDate() != null
            ? e.getRegDate().toLocalDateTime().format(DT_FMT)
            : null)
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public List<TrecordDto> findAll() {
    return repo.findAll().stream()
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<TrecordDto> searchByNumber(String number1, String number2) {
    if (number1 != null && number2 != null) {
      return repo.findByNumber1OrNumber2(number1, number2).stream()
          .map(this::toDto)
          .collect(Collectors.toList());
    } else if (number1 != null) {
      return repo.findByNumber1(number1).stream()
          .map(this::toDto)
          .collect(Collectors.toList());
    } else if (number2 != null) {
      return repo.findByNumber2(number2).stream()
          .map(this::toDto)
          .collect(Collectors.toList());
    } else {
      return findAll();
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
    // 엔티티로 변환해서 저장
    TrecordEntity e = new TrecordEntity();
    // leave recordSeq null (auto-generated)
    e.setCallStartDateTime(dto.getCallStartDateTime() != null
        ? Timestamp.valueOf(dto.getCallStartDateTime().replace(" ", "T"))
        : null);
    e.setCallEndDateTime(dto.getCallEndDateTime() != null
        ? Timestamp.valueOf(dto.getCallEndDateTime().replace(" ", "T"))
        : null);
    e.setAudioPlayTime(dto.getAudioPlayTime() != null
        ? Time.valueOf(dto.getAudioPlayTime())
        : null);
    e.setIoDiscdVal(dto.getIoDiscdVal());
    e.setNumber1(dto.getNumber1());
    e.setNumber2(dto.getNumber2());
    e.setAudioFileDir(dto.getAudioFileDir());
    e.setCallStatus(dto.getCallStatus());
    e.setRegDate(dto.getRegDate() != null
        ? Timestamp.valueOf(dto.getRegDate().replace(" ", "T"))
        : null);

    TrecordEntity saved = repo.save(e);
    return toDto(saved);
  }

  @Override
  @Transactional
  public TrecordDto update(Integer recordSeq, TrecordDto dto) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new IllegalArgumentException("녹취를 찾을 수 없습니다: " + recordSeq));

    if (dto.getCallStartDateTime() != null) {
      e.setCallStartDateTime(Timestamp.valueOf(dto.getCallStartDateTime().replace(" ", "T")));
    }
    if (dto.getCallEndDateTime() != null) {
      e.setCallEndDateTime(Timestamp.valueOf(dto.getCallEndDateTime().replace(" ", "T")));
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
      e.setRegDate(Timestamp.valueOf(dto.getRegDate().replace(" ", "T")));
    }

    TrecordEntity saved = repo.save(e);
    return toDto(saved);
  }

  @Override
  @Transactional
  public void delete(Integer recordSeq) {
    repo.deleteById(recordSeq);
  }
}
