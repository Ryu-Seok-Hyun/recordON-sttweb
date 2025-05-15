package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.UserPermission;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionRepository extends JpaRepository<UserPermission,Long> {
  Optional<UserPermission> findByGranteeSeqAndTargetSeq(Integer granteeSeq, Integer targetSeq);

  @Transactional
  void deleteByGranteeSeqAndTargetSeq(Integer granteeSeq, Integer targetSeq);

}
