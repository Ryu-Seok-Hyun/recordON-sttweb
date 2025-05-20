package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

  /** 중복 체크용 */
  boolean existsByGranteeUserIdAndTargetUserId(String granteeUserId, String targetUserId);

  /** (필요시) 단건 조회용 */
  Optional<UserPermission> findByGranteeUserIdAndTargetUserId(String granteeUserId, String targetUserId);

  /** 삭제용 */
  @Modifying
  @Transactional
  void deleteByGranteeUserIdAndTargetUserId(String granteeUserId, String targetUserId);

  // grant된 permLevel 이 requiredLevel 이상인 건수 조회
  long countByGranteeUserIdAndTargetUserIdAndPermLevelGreaterThanEqual(
      String granteeUserId,
      String targetUserId,
      Integer permLevel
  );
}
