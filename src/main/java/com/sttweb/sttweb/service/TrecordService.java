package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.sttweb.sttweb.dto.TmemberDto.Info;

public interface TrecordService {
  Page<TrecordDto> findAll(Pageable pageable);
  Page<TrecordDto> searchByNumber(String number1, String number2, Pageable pageable);
  Page<TrecordDto> search(
      String number1,
      String number2,
      String direction,   // ALL | IN | OUT
      String numberKind,  // ALL | PHONE | EXT
      String query,
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
