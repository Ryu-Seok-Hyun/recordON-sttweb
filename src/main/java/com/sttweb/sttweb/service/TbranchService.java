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
  List<TbranchEntity> findByIpAndPort(String ip, String port);

  /** 지사·본사에 설정된 모든 이메일 반환 */
  List<String> findAllEmails();

  /**  DB에 저장된 주/백업 IP:Port 리스트 반환 */
  List<TBranchEndpoint> findAllEndpoints();

  /**  특정 IP:Port 엔티티 찾아 상태 갱신 */
  void updateHealthStatus(String ip, int port, boolean isUp);

  List<TbranchEntity> findAllBranches();

  // 상태로만 조회
  Page<TbranchDto> findAllByStatus(boolean isAlive, Pageable pageable);
  // 검색+상태 조회
  Page<TbranchDto> searchWithStatus(String keyword, Boolean isAlive, Pageable pageable);


}