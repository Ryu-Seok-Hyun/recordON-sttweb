// ───────────────────────────────────────────────────────────────
// com.sttweb.sttweb.service.TrecordService
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TrecordService {
  /** 전체 조회 (페이징) */
  Page<TrecordDto> findAll(Pageable pageable);

  /** 번호 검색 (페이징) */
  Page<TrecordDto> searchByNumber(String number1, String number2, Pageable pageable);

  /** 단건 조회 */
  TrecordDto findById(Integer recordSeq);

  /** 생성 */
  TrecordDto create(TrecordDto dto);

  /** 수정 */
  TrecordDto update(Integer recordSeq, TrecordDto dto);

  /** 삭제 */
  void delete(Integer recordSeq);

  /** 본인 번호만 조회 (페이징) */
  Page<TrecordDto> findByUserNumber(String number, Pageable pageable);

  byte[] getAudioByIdAndUserSeq(Integer recordId, Integer targetUserSeq);

  Resource getFileByIdAndUserSeq(Integer recordId, Integer targetUserSeq);

}
