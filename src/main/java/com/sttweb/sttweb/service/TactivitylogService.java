package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TactivitylogDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TactivitylogService {

  TactivitylogDto createLog(TactivitylogDto dto);

  TactivitylogDto getLog(Integer activitySeq);

  void deleteLog(Integer activitySeq);

  Page<TactivitylogDto> getLogs(Pageable pageable);

  Page<TactivitylogDto> getLogsByUserId(String userId, Pageable pageable);

  /**
   * 필터(날짜범위, 구분, 검색필드/키워드) 적용 페이징 조회
   */
  Page<TactivitylogDto> getLogsWithFilter(
      String userId,
      String userLevel,
      String startCrtime,
      String endCrtime,
      String type,
      String searchField,
      String keyword,
      Pageable pageable
  );
}
