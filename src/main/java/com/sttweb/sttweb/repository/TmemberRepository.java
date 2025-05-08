package com.sttweb.sttweb.repository;

import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sttweb.sttweb.entity.TmemberEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TmemberRepository extends JpaRepository<TmemberEntity, Integer> {
  // 1) 회원가입 중복 체크(아이디 기준)
  Optional<TmemberEntity> findByUserId(String userId);

  // 2) 로그인 처리용(사원번호 기준)
  Optional<TmemberEntity> findByEmployeeId(Integer employeeId);

  @Modifying
  @Query("UPDATE TmemberEntity m SET m.roleSeq = :roleSeq WHERE m.memberSeq = :memberSeq")
  int updateRole(Integer memberSeq, Integer roleSeq);
}