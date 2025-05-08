// src/main/java/com/sttweb/sttweb/service/TrecordServiceImpl.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.entity.TrecordEntity;
import com.sttweb.sttweb.repository.TrecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrecordServiceImpl implements TrecordService {

  private final TrecordRepository repo;

  private TrecordDto toDto(TrecordEntity e) {
    return TrecordDto.builder()
        .recordSeq(e.getRecordSeq())
        .callStartDateTime(e.getCallStartDateTime())
        .callEndDateTime(e.getCallEndDateTime())
        .audioPlayTime(e.getAudioPlayTime())
        .ioDiscdVal(e.getIoDiscdVal())
        .number1(e.getNumber1())
        .number2(e.getNumber2())
        .audioFileDir(e.getAudioFileDir())
        .callStatus(e.getCallStatus())
        .regDate(e.getRegDate())
        .build();
  }

  private TrecordEntity toEntity(TrecordDto dto) {
    TrecordEntity e = new TrecordEntity();
    e.setRecordSeq(dto.getRecordSeq());
    e.setCallStartDateTime(dto.getCallStartDateTime());
    e.setCallEndDateTime(dto.getCallEndDateTime());
    e.setAudioPlayTime(dto.getAudioPlayTime());
    e.setIoDiscdVal(dto.getIoDiscdVal());
    e.setNumber1(dto.getNumber1());
    e.setNumber2(dto.getNumber2());
    e.setAudioFileDir(dto.getAudioFileDir());
    e.setCallStatus(dto.getCallStatus());
    e.setRegDate(dto.getRegDate());
    return e;
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
          .map(this::toDto).collect(Collectors.toList());
    } else if (number1 != null) {
      return repo.findByNumber1(number1).stream()
          .map(this::toDto).collect(Collectors.toList());
    } else if (number2 != null) {
      return repo.findByNumber2(number2).stream()
          .map(this::toDto).collect(Collectors.toList());
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
    TrecordEntity saved = repo.save(toEntity(dto));
    return toDto(saved);
  }

  @Override
  @Transactional
  public TrecordDto update(Integer recordSeq, TrecordDto dto) {
    TrecordEntity e = repo.findById(recordSeq)
        .orElseThrow(() -> new IllegalArgumentException("녹취를 찾을 수 없습니다: " + recordSeq));
    // 수정 가능한 필드 덮어쓰기
    e.setCallStartDateTime(dto.getCallStartDateTime());
    e.setCallEndDateTime(dto.getCallEndDateTime());
    e.setAudioPlayTime(dto.getAudioPlayTime());
    e.setIoDiscdVal(dto.getIoDiscdVal());
    e.setNumber1(dto.getNumber1());
    e.setNumber2(dto.getNumber2());
    e.setAudioFileDir(dto.getAudioFileDir());
    e.setCallStatus(dto.getCallStatus());
    e.setRegDate(dto.getRegDate());
    return toDto(repo.save(e));
  }

  @Override
  @Transactional
  public void delete(Integer recordSeq) {
    repo.deleteById(recordSeq);
  }
}
