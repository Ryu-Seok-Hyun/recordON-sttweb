// src/main/java/com/sttweb/sttweb/service/TrecordService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import java.util.List;

public interface TrecordService {
  /** 전체 조회 */
  List<TrecordDto> findAll();

  /** number1 또는 number2 로 검색 */
  List<TrecordDto> searchByNumber(String number1, String number2);

  /** 단건 조회 */
  TrecordDto findById(Integer recordSeq);

  /** 생성 */
  TrecordDto create(TrecordDto dto);

  /** 수정 */
  TrecordDto update(Integer recordSeq, TrecordDto dto);

  /** 삭제 */
  void delete(Integer recordSeq);
}
