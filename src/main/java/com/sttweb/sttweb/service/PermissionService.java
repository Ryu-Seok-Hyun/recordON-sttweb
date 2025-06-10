package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.repository.TmemberRepository;
import java.util.List;
import java.util.Objects;
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

  /** 권한 회수 */
  @Transactional
  public void revoke(String granteeUserId, String targetUserId) {
    permRepo.deleteByGranteeUserIdAndTargetUserId(granteeUserId, targetUserId);
  }

  /** 사용자 간 조회 권한으로 내선번호 추출 */
  @Transactional(readOnly = true)
  public List<String> findGrantedNumbers(Integer granteeMemberSeq) {
    TmemberEntity grantee = memberRepo.findById(granteeMemberSeq)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "사용자 정보 없음: " + granteeMemberSeq));
    String granteeUserId = grantee.getUserId();
    return permRepo.findByGranteeUserIdAndPermLevelGreaterThanEqual(granteeUserId, 1).stream()
        .map(UserPermission::getTargetUserId)
        .map(uid -> memberRepo.findByUserId(uid)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "권한 대상 정보 없음: " + uid)))
        .map(TmemberEntity::getNumber)
        .filter(Objects::nonNull)
        .toList();
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

  /**
   * 특정 사용자(granteeUserId)에게 부여된 권한 목록을 DTO로 반환
   */
  @Transactional(readOnly = true)
  public List<GrantDto> listGrantsFor(String granteeUserId) {
    return permRepo.findByGranteeUserId(granteeUserId).stream()
        .map(up -> GrantDto.builder()
            .granteeUserId(up.getGranteeUserId())
            .targetUserId(up.getTargetUserId())
            .permLevel(up.getPermLevel())
            .build()
        )
        .toList();
  }


}
