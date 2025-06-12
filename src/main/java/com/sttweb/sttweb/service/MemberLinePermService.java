// src/main/java/com/sttweb/sttweb/service/MemberLinePermService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.MemberLinePermDto;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.entity.TmemberLinePermEntity;
import com.sttweb.sttweb.entity.TmemberRoleEntity;
import com.sttweb.sttweb.entity.TrecordTelListEntity;
import com.sttweb.sttweb.repository.TmemberLinePermRepository;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.repository.TmemberRoleRepository;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberLinePermService {

  private final TmemberLinePermRepository permRepository;
  private final TmemberRepository        memberRepository;
  private final TrecordTelListRepository telListRepository;
  private final TmemberRoleRepository     roleRepository;
  private final PermissionService        permissionService;


  /**
   * roleSeq를 permLevel로 변환하는 헬퍼 함수
   */
  private int roleSeqToPermLevel(int roleSeq) {
    // 예시: 실제 프로젝트 권한 매핑에 맞게 수정!
    // (roleSeq 2 → permLevel 2, 3→3, 4→4, 나머지 1)
    switch (roleSeq) {
      case 2: return 2; // 조회 권한
      case 3: return 3; // 청취 권한
      case 4: return 4; // 다운로드 권한
      default: return 1; // NONE (권한 없음)
    }
  }

  @Transactional
  public boolean revokeLinePermission(int memberSeq, int lineId) {
    // 1. tmember_line_perm 삭제
    // 2. tuser_permission도 같이 삭제
    permissionService.revokeAndSyncLinePerm(memberSeq, lineId);
    return true;
  }


  public String getCallNumByLineId(Integer lineId) {
    return telListRepository.findById(lineId)
        .map(line -> line.getCallNum())
        .orElse("Unknown");
  }


  /**
   * Helper: TmemberLinePermEntity → MemberLinePermDto 로 변환
   */
  private MemberLinePermDto entityToDto(TmemberLinePermEntity e) {
    // 1) “권한을 부여받은” 회원 정보
    TmemberEntity member = e.getMember();
    String mapperUserId   = (member.getUserId() == null ? "" : member.getUserId());
    String mapperUserName = mapperUserId; // 현재 user_name 칼럼이 회원 테이블에 없으므로 userId 대체

    Integer mapperBranchSeq = member.getBranchSeq(); // ★ 여기서 branchSeq 가져오기

    // 2) “회선”(내선) 정보
    TrecordTelListEntity line = e.getLine();
    String callNum       = line.getCallNum();
    String callOwnerName = (line.getUserName() == null ? "" : line.getUserName());

    return MemberLinePermDto.builder()
        .memberSeq       (member.getMemberSeq())
        .userId          (mapperUserId)
        .userName        (mapperUserName)
        .branchSeq       (mapperBranchSeq)
        .lineId          (line.getId())
        .callNum         (callNum)
        .callOwnerName   (callOwnerName)
        .roleSeq         (e.getRole().getRoleSeq())
        .roleCode        (e.getRole().getRoleCode())
        .roleDescription (e.getRole().getDescription())
        .regtime         (e.getRegtime())
        .build();
  }

  /**
   * 0) 전체 매핑 조회
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getAllMappings() {
    return permRepository.findAll().stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  /**
   * 1) 특정 회원이 실제로 부여받은 권한 목록 조회
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getPermissionsByMember(Integer memberSeq) {
    return permRepository.findByMemberMemberSeq(memberSeq).stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  /**
   * 2) 특정 회선에 매핑된 회원별 권한 목록 조회
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getPermissionsByLine(Integer lineId) {
    return permRepository.findByLineId(lineId).stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  /**
   * 3) 회원(memberSeq)에게 회선(lineId)에 대한 권한(roleSeq)을 부여
   */
  @Transactional
  public boolean grantLinePermission(int memberSeq, int lineId, int roleSeq) {
    // 회선 권한 엔티티 직접 저장!
    TmemberEntity member = memberRepository.findById(memberSeq)
        .orElseThrow(() -> new IllegalArgumentException("memberSeq=" + memberSeq + " not found"));
    TrecordTelListEntity line = telListRepository.findById(lineId)
        .orElseThrow(() -> new IllegalArgumentException("lineId=" + lineId + " not found"));
    TmemberRoleEntity role = roleRepository.findById(roleSeq)
        .orElseThrow(() -> new IllegalArgumentException("roleSeq=" + roleSeq + " not found"));

    Optional<TmemberLinePermEntity> existingOpt = permRepository.findByMemberMemberSeqAndLineId(memberSeq, lineId);
    if (existingOpt.isPresent()) {
      TmemberLinePermEntity existing = existingOpt.get();
      existing.setRole(role);
      permRepository.save(existing);
    } else {
      TmemberLinePermEntity newPerm = TmemberLinePermEntity.builder()
          .member(member)
          .line(line)
          .role(role)
          .build();
      permRepository.save(newPerm);
    }

    // tuser_permission 동기화
    permissionService.grantAndSyncLinePerm(
        GrantDto.builder()
            .memberSeq(memberSeq)
            .lineId(lineId)
            .permLevel(roleSeqToPermLevel(roleSeq))
            .build()
    );
    return true;
  }


  /**
   * 4) 회원이 회선에 대해 가진 권한 매핑을 삭제
   */
  @Transactional
  public boolean revokeLinePermission(Integer memberSeq, Integer lineId) {
    Optional<TmemberLinePermEntity> existingOpt = permRepository.findByMemberMemberSeqAndLineId(memberSeq, lineId);
    if (existingOpt.isPresent()) {
      permRepository.delete(existingOpt.get());
      return true;
    }
    return false;

  }

  /**
   * 5) 특정 회원의 “전체 회선 + 권한 유무” 조회
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getAllLinesWithPerm(Integer memberSeq) {
    // (1) 해당 회원이 이미 부여받은 매핑 목록
    List<TmemberLinePermEntity> existingPerms =
        permRepository.findByMemberMemberSeq(memberSeq);

    // (2) lineId → 매핑 엔티티 맵핑
    Map<Integer, TmemberLinePermEntity> permByLineId = existingPerms.stream()
        .collect(Collectors.toMap(
            perm -> perm.getLine().getId(),
            perm -> perm
        ));

    // (3) 전체 회선 목록
    List<TrecordTelListEntity> allLines = telListRepository.findAll();

    // (4) “권한 조회를 요청한” 회원 정보 (회원이 없을 수 있으므로 null 체크)
    TmemberEntity requestMemberEntity = memberRepository.findById(memberSeq).orElse(null);
    String requestUserId   = (requestMemberEntity == null ? "" : requestMemberEntity.getUserId());
    String requestUserName = requestUserId;
    Integer requestBranchSeq = (requestMemberEntity == null ? null : requestMemberEntity.getBranchSeq());

    List<MemberLinePermDto> result = new ArrayList<>();

    for (TrecordTelListEntity line : allLines) {
      TmemberLinePermEntity perm = permByLineId.get(line.getId());
      if (perm != null) {
        // (가) 매핑이 있는 경우 → 실제 매핑된 값으로 내려줌
        TmemberEntity m        = perm.getMember();
        String        mUserId  = (m.getUserId() == null ? "" : m.getUserId());
        String        mUserName = mUserId;
        Integer       mBranchSeq = m.getBranchSeq();
        String        ownerName = (line.getUserName() == null ? "" : line.getUserName());

        result.add(MemberLinePermDto.builder()
            .memberSeq       (m.getMemberSeq())
            .userId          (mUserId)
            .userName        (mUserName)
            .branchSeq       (mBranchSeq)
            .lineId          (line.getId())
            .callNum         (line.getCallNum())
            .callOwnerName   (ownerName)
            .roleSeq         (perm.getRole().getRoleSeq())
            .roleCode        (perm.getRole().getRoleCode())
            .roleDescription (perm.getRole().getDescription())
            .regtime         (perm.getRegtime())
            .build()
        );
      } else {
        // (나) 매핑이 없는 경우 → NONE 권한으로 내려줌
        String ownerName = (line.getUserName() == null ? "" : line.getUserName());

        result.add(MemberLinePermDto.builder()
            .memberSeq       (memberSeq)
            .userId          (requestUserId)
            .userName        (requestUserName)
            .branchSeq       (requestBranchSeq)
            .lineId          (line.getId())
            .callNum         (line.getCallNum())
            .callOwnerName   (ownerName)
            .roleSeq         (1)                    // NONE
            .roleCode        ("NONE")
            .roleDescription ("권한 없음")
            .regtime         (null)
            .build()
        );
      }
    }

    return result;
  }

  /**
   * 6) 모든 회원 × 모든 회선 권한 조회
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getAllMembersAllLinesPerm() {
    List<MemberLinePermDto> combined = new ArrayList<>();
    List<TmemberEntity>     allMembers = memberRepository.findAll();

    for (TmemberEntity member : allMembers) {
      Integer memberSeq = member.getMemberSeq();
      combined.addAll(getAllLinesWithPerm(memberSeq));
    }
    return combined;
  }
}
