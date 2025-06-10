package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

public interface TrecordService {

  Page<TrecordDto> findAll(Pageable pageable);

  Page<TrecordDto> searchByNumber(String number1, String number2, Pageable pageable);

  /**
   * 전체 + 통합검색 + 시간범위
   *
   * @param number1    검색할 첫 번호 (or null)
   * @param number2    두 번째 번호 (or null)
   * @param direction  ALL|IN|OUT
   * @param numberKind ALL|PHONE|EXT
   * @param query      키워드
   * @param start      시작 시각 (or null)
   * @param end        종료 시각 (or null)
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

  Page<TrecordDto> findAllByBranch(Integer branchSeq, Pageable pageable);

  TrecordDto create(TrecordDto dto);

  TrecordDto update(Integer recordSeq, TrecordDto dto);

  void delete(Integer recordSeq);

  Page<TrecordDto> findByUserNumber(String number, Pageable pageable);

  Resource getFileByIdAndUserSeq(Integer recordId, Integer targetUserSeq);

  Resource getFile(Integer recordSeq);

  /** 다중 번호(equal) 검색 */
  Page<TrecordDto> searchByNumbers(List<String> numbers, Pageable pageable);

  /** 지점별 IN/OUT 카운트 (direction="IN" 또는 "OUT", "ALL"일 땐 전체 건수 반환) */
  long countByBranchAndDirection(Integer branchSeq, String direction);

  /** 서비스에 내선번호 기반 메서드 (레거시 - 지점 구분 없음) */
  Page<TrecordDto> searchByCallNums(List<String> callNums, Pageable pageable);

  // ─────────────────────────────────────────────────────────────
  // NEW! 내선번호와 전화번호 구분 검색 메서드들
  // ─────────────────────────────────────────────────────────────

  /**
   * 특정 지점의 내선번호(4자리)만 검색
   */
  Page<TrecordDto> searchByBranchAndExtensions(Integer branchSeq, List<String> extensions, Pageable pageable);

  /**
   * 내선번호와 전화번호를 구분해서 혼합 검색
   */
  Page<TrecordDto> searchByMixedNumbers(List<String> numbers, Pageable pageable);
}