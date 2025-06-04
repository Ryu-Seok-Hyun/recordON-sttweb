// src/main/java/com/sttweb/sttweb/repository/TmemberRepository.java
package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TmemberEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

public interface TmemberRepository extends JpaRepository<TmemberEntity, Integer> {

  /** 아이디(userId) 중복 체크 */
  boolean existsByUserId(String userId);

  /** userId 기준 단일 조회 */
  Optional<TmemberEntity> findByUserId(String userId);

  /** userId 또는 number 컬럼에 keyword가 포함된 레코드 페이징 조회 */
  Page<TmemberEntity> findByUserIdContainingOrNumberContaining(
      String userIdKeyword,
      String numberKeyword,
      Pageable pageable
  );

  /** number(내선번호) 기준 단일 조회 */
  Optional<TmemberEntity> findByNumber(String number);

  // ==== 지사 관리자 전용 페이징 조회/검색 ====
  Page<TmemberEntity> findByBranchSeq(Integer branchSeq, Pageable pageable);

  @Query("SELECT e FROM TmemberEntity e "
      + "WHERE e.branchSeq = :branchSeq "
      + "  AND (e.userId LIKE %:kw% OR e.number LIKE %:kw%)")
  Page<TmemberEntity> findByBranchSeqAnd(
      @Param("branchSeq") Integer branchSeq,
      @Param("kw")         String keyword,
      Pageable pageable
  );

  @Query("SELECT m FROM TmemberEntity m JOIN TbranchEntity b ON m.branchSeq = b.branchSeq "
      + " WHERE LOWER(b.companyName) LIKE LOWER(CONCAT('%', :branchName, '%'))")
  Page<TmemberEntity> findByBranchNameContaining(
      @Param("branchName") String branchName,
      Pageable pageable
  );

  @Query("""
    SELECT m
      FROM TmemberEntity m
      JOIN TbranchEntity b ON m.branchSeq = b.branchSeq
     WHERE LOWER(b.companyName) LIKE LOWER(CONCAT('%', :branchName, '%'))
       AND (m.userId LIKE %:kw% OR m.number LIKE %:kw%)
  """)
  Page<TmemberEntity> findByBranchNameContainingAndKeyword(
      @Param("branchName") String branchName,
      @Param("kw")         String keyword,
      Pageable pageable
  );

  /** 브랜치별 userId 중복 개수 조회 */
  long countByUserIdAndBranchSeq(String userId, Integer branchSeq);

  // ==== 회원 권한 변경(UPDATE) ====
  @Modifying
  @Transactional
  @Query("UPDATE TmemberEntity m SET m.roleSeq = :roleSeq WHERE m.memberSeq = :memberSeq")
  int updateRole(
      @Param("memberSeq") Integer memberSeq,
      @Param("roleSeq")   Integer roleSeq
  );
}
