package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import java.util.List;

public interface TrecordService {
  List<TrecordDto> searchByNumber(String number1, String number2);

  List<TrecordDto> findAll();
  List<TrecordDto> findByNumber(String number1, String number2);
  TrecordDto findById(Integer recordSeq);
  TrecordDto create(TrecordDto dto);
  TrecordDto update(Integer recordSeq,TrecordDto dto);
  void delete(Integer recordSeq);

}
