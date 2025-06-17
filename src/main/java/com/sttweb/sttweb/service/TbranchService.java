package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.entity.TbranchEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface TbranchService {

  List<TbranchEntity> findAllEntities();

  // 페이징 전체 조회
  Page<TbranchDto> findAll(Pageable pageable);

  // 회사명 검색
  Page<TbranchDto> search(String keyword, Pageable pageable);

  // 내부망 IP(p_ip) 로 엔티티(Optional) 조회
  Optional<TbranchEntity> findBypIp(String pIp);

  // public IP(pb_ip) 로 엔티티(Optional) 조회
  Optional<TbranchEntity> findByPbIp(String pbIp);

  // public IP 로 DTO 조회 (필요 시)
  TbranchDto findByPublicIp(String ip);

  // branchSeq 로 조회
  TbranchDto findById(Integer branchSeq);

  // 신규 지점 생성
  TbranchDto createBranch(TbranchDto dto);

  // 지점 수정
  TbranchDto update(Integer branchSeq, TbranchDto dto);

  // 활성/비활성 변경
  void changeStatus(Integer branchSeq, boolean active);

  // hqYn = "0" 인 본사지점 하나 조회
  Optional<TbranchEntity> findHqBranch();

  // fallback 용: branchSeq → entity
  TbranchEntity findEntityBySeq(Integer branchSeq);

  // TbranchService.java (interface)
  default String getPIpByBranchSeq(Integer branchSeq) {
    if (branchSeq == null || branchSeq == 0) return null;
    try {
      return findById(branchSeq).getPIp();
    } catch (Exception e) {
      return null;
    }
  }
  Optional<TbranchEntity> findByIpAndPort(String ip, String port);

}
