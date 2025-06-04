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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberLinePermService {

  private final TmemberLinePermRepository permRepository;
  private final TmemberRepository memberRepository;
  private final TrecordTelListRepository telListRepository;
  private final TmemberRoleRepository roleRepository;

  /**
   * 특정 회원(memberSeq)이 가진 회선별 권한 목록을 DTO로 반환
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getPermissionsByMember(Integer memberSeq) {
    List<TmemberLinePermEntity> entities =
        permRepository.findByMemberMemberSeq(memberSeq);
    return entities.stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  /**
   * 특정 회선(lineId)에 매핑된 회원별 권한 목록을 DTO로 반환
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getPermissionsByLine(Integer lineId) {
    List<TmemberLinePermEntity> entities =
        permRepository.findByLineId(lineId);
    return entities.stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  /**
   * 회원(memberSeq)에게 회선(lineId)에 대한 권한(roleSeq)을 부여
   * - memberSeq, lineId, roleSeq가 실제로 존재하는지 검증
   * - 중복이 아니면 INSERT, 이미 존재하면 아무 작업도 하지 않음
   * @return true=부여 성공, false=이미 존재하거나 잘못된 요청
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
        // regtime은 @CreationTimestamp가 자동 설정
        .build();
    permRepository.save(newPerm);
    return true;
  }

  /**
   * 회원(memberSeq)이 회선(lineId)에 대해 가진 권한 매핑을 삭제
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

  /** Helper: 엔티티→DTO 변환 */
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
   * 0) 전체 매핑 조회
   *   → tmember_line_perm 테이블에 저장된 모든 행을 DTO로 변환해서 반환
   */
  @Transactional(readOnly = true)
  public List<MemberLinePermDto> getAllMappings() {
    List<TmemberLinePermEntity> entities = permRepository.findAll();
    return entities.stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }


}
