package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

public interface TrecordService {
  Page<TrecordDto> findAll(Pageable pageable);
  Page<TrecordDto> searchByNumber(String number1, String number2, Pageable pageable);


  /**
   * 전체 + 통합검색 + 시간범위
   * @param number1   검색할 첫 번호 (or null)
   * @param number2   두 번째 번호 (or null)
   * @param direction ALL|IN|OUT
   * @param numberKind ALL|PHONE|EXT
   * @param query     키워드
   * @param start     시작 시각 (or null)
   * @param end       종료 시각 (or null)
   */
  Page<TrecordDto> search(
      String number1,
      String number2,
      String direction,
      String numberKind,
      String query,
      LocalDateTime start,
      LocalDateTime end,
      Pageable pageable
  );


  Page<TrecordDto> advancedSearch(
      String direction,
      String numberKind,
      String q,
      Pageable pageable,
      Info me
  );
  TrecordDto findById(Integer recordSeq);
  TrecordDto create(TrecordDto dto);
  TrecordDto update(Integer recordSeq, TrecordDto dto);
  void delete(Integer recordSeq);
  Page<TrecordDto> findByUserNumber(String number, Pageable pageable);
  Resource getFileByIdAndUserSeq(Integer recordId, Integer targetUserSeq);
  Resource getFile(Integer recordSeq);
}
