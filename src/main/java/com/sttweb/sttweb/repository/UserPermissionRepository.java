package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
  boolean existsByMemberSeqAndLineId(Integer memberSeq, Integer lineId);
  void deleteByMemberSeqAndLineId(Integer memberSeq, Integer lineId);
  List<UserPermission> findByMemberSeqAndPermLevelGreaterThanEqual(Integer memberSeq, Integer permLevel);
  List<UserPermission> findByMemberSeq(Integer memberSeq);
  long countByMemberSeqAndLineIdAndPermLevelGreaterThanEqual(Integer memberSeq, Integer lineId, int permLevel);
  Optional<UserPermission> findByMemberSeqAndLineId(Integer memberSeq, Integer lineId);


}
