// src/main/java/com/sttweb/sttweb/repository/TmemberLinePermRepository.java
package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TmemberLinePermEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TmemberLinePermRepository extends JpaRepository<TmemberLinePermEntity, Integer> {

  /** 특정 회원(memberSeq)이 가진 회선별 권한 조회 */
  List<TmemberLinePermEntity> findByMemberMemberSeq(Integer memberSeq);

  /** 특정 회선(lineId)에 매핑된 회원별 권한 조회 */
  List<TmemberLinePermEntity> findByLineId(Integer lineId);

  /** 회원(memberSeq) + 회선(lineId) 조합 단일 조회 */
  TmemberLinePermEntity findByMemberMemberSeqAndLineId(Integer memberSeq, Integer lineId);
}
