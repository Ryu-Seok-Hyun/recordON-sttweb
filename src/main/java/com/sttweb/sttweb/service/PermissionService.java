package com.sttweb.sttweb.service;

import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
  private final UserPermissionRepository repo;

  public PermissionService(UserPermissionRepository repo) {
    this.repo = repo;
  }

  /** targetSeq에 대해 granteeSeq가 요청한 최소 레벨 이상인지 */
  public boolean hasLevel(Integer granteeSeq, Integer targetSeq, int requiredLevel) {
    return repo.findByGranteeSeqAndTargetSeq(granteeSeq, targetSeq)
        .map(UserPermission::getPermLevel)
        .filter(level -> level >= requiredLevel)
        .isPresent();
  }
}
