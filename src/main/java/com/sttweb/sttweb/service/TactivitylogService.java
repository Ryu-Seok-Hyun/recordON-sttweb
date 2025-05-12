// src/main/java/com/sttweb/sttweb/service/TactivitylogService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.entity.TactivitylogEntity;
import com.sttweb.sttweb.dto.TactivitylogDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TactivitylogService {


  /** 새 로그 생성 (DTO) */
  TactivitylogDto createLog(TactivitylogDto dto);

  /** 단건 조회 */
  TactivitylogDto getLog(Integer activitySeq);

  /** 페이징 조회 */
  Page<TactivitylogDto> getLogs(Pageable pageable);

  /** 삭제 */
  void deleteLog(Integer activitySeq);
}
