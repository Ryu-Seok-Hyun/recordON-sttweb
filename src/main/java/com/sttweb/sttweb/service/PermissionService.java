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
  private final TmemberRepository         memberRepo;

  /**
   * 권한(Grant) 등록
   */
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

    // 저장
    UserPermission p = new UserPermission();
    p.setGranteeUserId(req.getGranteeUserId());
    p.setTargetUserId(req.getTargetUserId());
    p.setPermLevel(req.getPermLevel());
    permRepo.save(p);
  }

  /**
   * granterMemberSeq 가 targetMemberSeq 에 대해
   * permLevel(requiredLevel) 이상의 권한을 갖고 있는지 검사
   */
  @Transactional(readOnly = true)
  public boolean hasLevel(
      Integer granterMemberSeq,
      Integer targetMemberSeq,
      int requiredLevel
  ) {
    // 1) memberSeq → userId 로 변환
    TmemberEntity granter = memberRepo.findById(granterMemberSeq)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "권한 검사용 사용자 정보를 찾을 수 없습니다: " + granterMemberSeq
        ));
    TmemberEntity target  = memberRepo.findById(targetMemberSeq)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "권한 대상 사용자 정보를 찾을 수 없습니다: " + targetMemberSeq
        ));

    String granteeUserId = granter.getUserId();
    String targetUserId  = target.getUserId();

    // 2) Grant 테이블에서 permLevel ≥ requiredLevel 인 레코드가 있으면 true
    return permRepo.countByGranteeUserIdAndTargetUserIdAndPermLevelGreaterThanEqual(
        granteeUserId,
        targetUserId,
        requiredLevel
    ) > 0;
  }
}
