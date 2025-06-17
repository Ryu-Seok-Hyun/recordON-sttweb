package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TactivitylogDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TactivitylogService {

  /* CRUD */
  TactivitylogDto createLog(TactivitylogDto dto);
  TactivitylogDto getLog(Integer activitySeq);
  void deleteLog(Integer activitySeq);

  /* 단순 조회 */
  Page<TactivitylogDto> getLogs(Pageable pageable);
  Page<TactivitylogDto> getLogsByUserId(String userId, Pageable pageable);

  /* 권한 + 기간 + 검색 통합 필터  ★ branchSeq 포함 */
  Page<TactivitylogDto> getLogsWithFilter(
      String  userId,
      String  userLevel,
      Integer branchSeq,      // ← 인터페이스에도 반드시 존재!
      String  startCrtime,
      String  endCrtime,
      String  type,
      String  searchField,
      String  keyword,
      Pageable pageable
  );
}
