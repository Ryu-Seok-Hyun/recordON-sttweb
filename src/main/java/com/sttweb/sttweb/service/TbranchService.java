// src/main/java/com/sttweb/sttweb/service/TbranchService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.entity.TbranchEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TbranchService {
  /** 페이징 처리된 전체 조회 */
  Page<TbranchDto> findAll(Pageable pageable);

  /** 단건 조회 */
  TbranchDto findById(Integer branchSeq);

  /** 생성 */
  TbranchDto createBranch(TbranchDto dto);

  /** 수정 */
  TbranchDto update(Integer branchSeq, TbranchDto dto);

  /** 활성·비활성 처리 */
  void changeStatus(Integer branchSeq, boolean active);

  Page<TbranchDto> search(String keyword, Pageable pageable);

  TbranchDto findByPublicIp(String ip);

  Optional<TbranchEntity> findBypIp(String pIp);
}
