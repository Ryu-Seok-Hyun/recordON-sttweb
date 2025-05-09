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
    TrecordEntity e = new TrecordEntity();

    // 공백 그대로 Timestamp.valueOf에 넘깁니다
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
}
