// src/main/java/com/sttweb/sttweb/service/TbranchService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.entity.TbranchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface TbranchService {

  /** 페이징 처리된 전체 조회 */
  Page<TbranchDto> findAll(Pageable pageable);

  /** 단건 조회 (DTO 반환) */
  TbranchDto findById(Integer branchSeq);

  /** Entity 자체를 ID로 조회 */
  TbranchEntity findEntityBySeq(Integer branchSeq);

  /** 생성 */
  TbranchDto createBranch(TbranchDto dto);

  /** 수정 */
  TbranchDto update(Integer branchSeq, TbranchDto dto);

  /** 활성·비활성 처리 */
  void changeStatus(Integer branchSeq, boolean active);

  Page<TbranchDto> search(String keyword, Pageable pageable);

  TbranchDto findByPublicIp(String ip);

  Optional<TbranchEntity> findBypIp(String pIp);

  Optional<TbranchEntity> findByPbIp(String pbIp);

  /**
   * hqYn = "0"인(본사) 지점을 하나 가져온다.
   * 실제 구현에서는 findTopByHqYn("0") 을 호출합니다.
   */
  Optional<TbranchEntity> findHqBranch();
}
