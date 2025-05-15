// src/main/java/com/sttweb/sttweb/service/PermissionService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.GrantDto;               // ★ 변경
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PermissionService {

  private final UserPermissionRepository permRepo;

  @Transactional
  public void grant(GrantDto req) {
    // 중복 체크
    if (permRepo.existsByGranteeUserIdAndTargetUserId(
        req.getGranteeUserId(), req.getTargetUserId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Permission already exists: "
              + req.getGranteeUserId() + " → " + req.getTargetUserId()
      );
    }

    // 엔티티 생성 및 저장
    UserPermission p = new UserPermission();
    p.setGranteeUserId(req.getGranteeUserId());
    p.setTargetUserId(req.getTargetUserId());
    p.setPermLevel(req.getPermLevel());
    permRepo.save(p);
  }

  @Transactional
  public void revoke(String granteeUserId, String targetUserId) {
    permRepo.deleteByGranteeUserIdAndTargetUserId(granteeUserId, targetUserId);
  }
}
