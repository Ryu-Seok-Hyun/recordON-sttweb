// src/main/java/com/sttweb/sttweb/service/MemberLinePermService.java
package com.sttweb.sttweb.service;

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
  private final TmemberRepository memberRepository;
  private final TrecordTelListRepository telListRepository;
  private final TmemberRoleRepository roleRepository;

  /**
   * 0) 전체 매핑 조회
   *   → tmember_line_perm 테이블의 모든 행을 DTO 리스트로 반환
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getAllMappings() {
    return permRepository.findAll().stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  /**
   * 1) 특정 회원(memberSeq)이 가진 회선별 권한 목록(실제 부여된 것만) 반환
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getPermissionsByMember(Integer memberSeq) {
    return permRepository.findByMemberMemberSeq(memberSeq).stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  /**
   * 2) 특정 회선(lineId)에 매핑된 회원별 권한 목록 반환
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getPermissionsByLine(Integer lineId) {
    return permRepository.findByLineId(lineId).stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  /**
   * 3) 회원(memberSeq)에게 회선(lineId)에 대한 권한(roleSeq)을 부여
   *    - 이미 부여되어 있으면 false, 성공하면 true
   */
  @Transactional
  public boolean grantLinePermission(Integer memberSeq, Integer lineId, Integer roleSeq) {
    TmemberEntity member = memberRepository.findById(memberSeq)
        .orElseThrow(() -> new IllegalArgumentException("memberSeq=" + memberSeq + " not found"));
    TrecordTelListEntity line = telListRepository.findById(lineId)
        .orElseThrow(() -> new IllegalArgumentException("lineId=" + lineId + " not found"));
    TmemberRoleEntity role = roleRepository.findById(roleSeq)
        .orElseThrow(() -> new IllegalArgumentException("roleSeq=" + roleSeq + " not found"));

    TmemberLinePermEntity existing =
        permRepository.findByMemberMemberSeqAndLineId(memberSeq, lineId);
    if (existing != null) {
      // 이미 권한이 부여된 상태
      return false;
    }

    TmemberLinePermEntity newPerm = TmemberLinePermEntity.builder()
        .member(member)
        .line(line)
        .role(role)
        // regtime은 @CreationTimestamp가 자동 설정됨
        .build();
    permRepository.save(newPerm);
    return true;
  }

  /**
   * 4) 회원(memberSeq)이 회선(lineId)에 대해 가진 권한 매핑을 삭제
   * @return true=삭제 성공, false=존재하지 않음
   */
  @Transactional
  public boolean revokeLinePermission(Integer memberSeq, Integer lineId) {
    TmemberLinePermEntity existing =
        permRepository.findByMemberMemberSeqAndLineId(memberSeq, lineId);
    if (existing == null) {
      return false;
    }
    permRepository.delete(existing);
    return true;
  }

  /** Helper: 엔티티 → DTO 변환 */
  private MemberLinePermDto entityToDto(TmemberLinePermEntity e) {
    return MemberLinePermDto.builder()
        .memberSeq      (e.getMember().getMemberSeq())
        .userId         (e.getMember().getUserId())
        .lineId         (e.getLine().getId())
        .callNum        (e.getLine().getCallNum())
        .roleSeq        (e.getRole().getRoleSeq())
        .roleCode       (e.getRole().getRoleCode())
        .roleDescription(e.getRole().getDescription())
        .regtime        (e.getRegtime())
        .build();
  }

  /**
   * 5) 전체 회선 목록 + 해당 회원이 가진 권한 유무를 모두 리턴
   *    → 회원(memberSeq)에 매핑된 권한이 있으면 해당 권한,
   *       매핑이 없으면 기본 “NONE”으로 표시
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getAllLinesWithPerm(Integer memberSeq) {
    // 1) 회원이 이미 부여받은 권한 매핑을 모두 조회
    List<TmemberLinePermEntity> existingPerms =
        permRepository.findByMemberMemberSeq(memberSeq);

    // 2) lineId → 그 권한 엔티티 맵으로 변환
    Map<Integer, TmemberLinePermEntity> permByLineId = existingPerms.stream()
        .collect(Collectors.toMap(
            e -> e.getLine().getId(),
            e -> e
        ));

    // 3) 전체 회선 목록 조회
    List<TrecordTelListEntity> allLines = telListRepository.findAll();

    // 4) 회원 userId 조회 (매핑이 없을 때에도 포함하기 위함)
    String memberUserId = memberRepository.findById(memberSeq)
        .map(TmemberEntity::getUserId)
        .orElse(""); // 회원 자체가 없으면 빈 문자열로 처리

    // 5) 모든 회선을 순회하면서, 매핑이 있으면 실제 권한, 없으면 “NONE”으로 DTO 생성
    List<MemberLinePermDto> result = new ArrayList<>();

    for (TrecordTelListEntity line : allLines) {
      TmemberLinePermEntity perm = permByLineId.get(line.getId());
      if (perm != null) {
        // 이미 권한 매핑이 있는 경우 → 실제 권한 정보 사용
        result.add(MemberLinePermDto.builder()
            .memberSeq(memberSeq)
            .userId(memberUserId)
            .lineId(line.getId())
            .callNum(line.getCallNum())
            .roleSeq(perm.getRole().getRoleSeq())
            .roleCode(perm.getRole().getRoleCode())
            .roleDescription(perm.getRole().getDescription())
            .regtime(perm.getRegtime())
            .build()
        );
      } else {
        // 매핑이 없는 경우 → NONE 처리
        result.add(MemberLinePermDto.builder()
            .memberSeq(memberSeq)
            .userId(memberUserId)
            .lineId(line.getId())
            .callNum(line.getCallNum())
            .roleSeq(1)               // roleSeq=1(=NONE)이라고 가정
            .roleCode("NONE")
            .roleDescription("권한 없음")
            .regtime(null)            // 매핑이 없으므로 regtime도 null
            .build()
        );
      }
    }

    return result;
  }

  /**
   * 6) 전체 회원(member)에 대해서도 “전체 회선+권한 유무”를 리턴하고 싶다면,
   *    아래 메서드를 추가로 구현할 수 있습니다.
   *    → 회원 목록을 순회하면서 getAllLinesWithPerm()을 호출하도록 구성
   *
   * @return memberSeq, userId, lineId, callNum, roleSeq, roleCode, roleDescription, regtime 을 담은 DTO 리스트
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getAllMembersAllLinesPerm() {
    List<MemberLinePermDto> combined = new ArrayList<>();

    // 1) 모든 회원 조회
    List<TmemberEntity> allMembers = memberRepository.findAll();
    // 2) 회원별로 getAllLinesWithPerm() 호출 후 결과를 합친다
    for (TmemberEntity member : allMembers) {
      Integer memberSeq = member.getMemberSeq();
      List<MemberLinePermDto> perMemberList = getAllLinesWithPerm(memberSeq);
      combined.addAll(perMemberList);
    }

    return combined;
  }
}
