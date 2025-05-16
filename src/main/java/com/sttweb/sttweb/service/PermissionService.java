package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.repository.TmemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PermissionService {

  private final UserPermissionRepository permRepo;
  private final TmemberRepository memberRepo;

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

  /**
   * 지정된 granteeMemberSeq가 targetMemberSeq에 대해 requiredLevel 이상의 권한을 가졌는지 확인
   */
  public boolean hasLevel(
      Integer granteeMemberSeq,
      Integer targetMemberSeq,
      Integer requiredLevel
  ) {
    // 회원 조회
    TmemberEntity grantee = memberRepo.findById(granteeMemberSeq)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Grantee user not found: " + granteeMemberSeq));
    TmemberEntity target = memberRepo.findById(targetMemberSeq)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Target user not found: " + targetMemberSeq));

    // 권한 조회 및 레벨 비교
    return permRepo.findByGranteeUserIdAndTargetUserId(
            grantee.getUserId(), target.getUserId())
        .map(p -> p.getPermLevel() >= requiredLevel)
        .orElse(false);
  }
}
