// src/main/java/com/sttweb/sttweb/repository/TmemberRepository.java
package com.sttweb.sttweb.repository;

import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sttweb.sttweb.entity.TmemberEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TmemberRepository extends JpaRepository<TmemberEntity, Integer> {

  boolean existsByUserId(String userId);

  // 1) 회원가입 중복 체크(아이디 기준)
  Optional<TmemberEntity> findByUserId(String userId);


  @Modifying
  @Transactional
  @Query("UPDATE TmemberEntity m SET m.roleSeq = :roleSeq WHERE m.memberSeq = :memberSeq")
  int updateRole(
      @Param("memberSeq") Integer memberSeq,
      @Param("roleSeq")   Integer roleSeq
  );

  // userId 또는 number 컬럼에 keyword가 포함된 레코드 페이징 조회
  Page<TmemberEntity> findByUserIdContainingOrNumberContaining(
      String userIdKeyword,
      String numberKeyword,
      Pageable pageable
  );

  Optional<TmemberEntity> findByNumber(String number);

  // === 지사 관리자 전용 페이징 조회/검색용 ===
  Page<TmemberEntity> findByBranchSeq(Integer branchSeq, Pageable pageable);

  @Query("SELECT e FROM TmemberEntity e "
      + "WHERE e.branchSeq = :branchSeq "
      + "  AND (e.userId LIKE %:kw% OR e.number LIKE %:kw%)")
  Page<TmemberEntity> findByBranchSeqAnd(
      @Param("branchSeq") Integer branchSeq,
      @Param("kw")         String keyword,
      Pageable pageable
  );

  /** 지점명으로 사용자 페이징 조회 */
  @Query("SELECT m FROM TmemberEntity m JOIN TbranchEntity b ON m.branchSeq = b.branchSeq " +
      " WHERE LOWER(b.companyName) LIKE LOWER(CONCAT('%', :branchName, '%'))")
  Page<TmemberEntity> findByBranchNameContaining(
      @Param("branchName") String branchName,
      Pageable pageable
  );

  // <<< 추가 >>> 브랜치별, userId 중복 개수 조회
  long countByUserIdAndBranchSeq(String userId, Integer branchSeq);

  /**
   * 본사: 지점명에 포함되고 (userId OR number) 에 keyword 가 포함된 레코드 페이징 조회
   */
  @Query("""
  SELECT m
    FROM TmemberEntity m
    JOIN TbranchEntity b ON m.branchSeq = b.branchSeq
   WHERE LOWER(b.companyName) LIKE LOWER(CONCAT('%', :branchName, '%'))
     AND (m.userId   LIKE %:kw%
       OR m.number   LIKE %:kw%)
""")
  Page<TmemberEntity> findByBranchNameContainingAndKeyword(
      @Param("branchName") String branchName,
      @Param("kw")         String keyword,
      Pageable pageable
  );

}
