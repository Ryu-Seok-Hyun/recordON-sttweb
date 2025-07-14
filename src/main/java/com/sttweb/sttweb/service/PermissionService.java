package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.entity.TmemberLinePermEntity;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.repository.TmemberLinePermRepository;
import com.sttweb.sttweb.repository.TmemberRoleRepository;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.repository.TmemberRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PermissionService {
  private final UserPermissionRepository permRepo;
  private final TmemberLinePermRepository memberLinePermRepo;
  private final TmemberRoleRepository roleRepo;
  private final TrecordTelListRepository lineRepo;
  private final TmemberRepository memberRepo;

  @Transactional
  public void grantAndSyncLinePerm(GrantDto req) {
    // 1. tuser_permission 갱신 또는 생성
    var upOpt = permRepo.findByMemberSeqAndLineId(req.getMemberSeq(), req.getLineId());
    UserPermission up = upOpt.map(existing -> {
      existing.setPermLevel(req.getPermLevel());
      return existing;
    }).orElseGet(() -> {
      UserPermission created = new UserPermission();
      created.setMemberSeq(req.getMemberSeq());
      created.setLineId(req.getLineId());
      created.setPermLevel(req.getPermLevel());
      return created;
    });
    permRepo.save(up);

    // 2. tmember_line_perm 동기화
    // — 조회 권한(2)만 주는 경우: 기존에 sync 되어 있던 청취·다운로드 권한 제거
    if (req.getPermLevel().equals(2)) {
      memberLinePermRepo.findByMemberMemberSeqAndLineId(req.getMemberSeq(), req.getLineId())
          .ifPresent(memberLinePermRepo::delete);
      return;
    }

    // — 청취(3) 또는 다운로드(4) 권한인 경우에만 role sync
    Integer roleSeq = permLevelToRoleSeq(req.getPermLevel());
    memberLinePermRepo.findByMemberMemberSeqAndLineId(req.getMemberSeq(), req.getLineId())
        .ifPresentOrElse(
            linePerm -> {
              linePerm.setRole(roleRepo.findById(roleSeq).orElseThrow());
              memberLinePermRepo.save(linePerm);
            },
            () -> {
              TmemberLinePermEntity newPerm = TmemberLinePermEntity.builder()
                  .member(memberRepo.findById(req.getMemberSeq()).orElseThrow())
                  .line(lineRepo.findById(req.getLineId()).orElseThrow())
                  .role(roleRepo.findById(roleSeq).orElseThrow())
                  .build();
              memberLinePermRepo.save(newPerm);
            }
        );
  }



  @Transactional
  public void revokeAndSyncLinePerm(Integer memberSeq, Integer lineId) {
    // 1. tuser_permission 삭제
    permRepo.deleteByMemberSeqAndLineId(memberSeq, lineId);

    // 2. tmember_line_perm 삭제 (Optional 처리)
    Optional<TmemberLinePermEntity> linePermOpt = memberLinePermRepo.findByMemberMemberSeqAndLineId(memberSeq, lineId);
    linePermOpt.ifPresent(memberLinePermRepo::delete);
  }


  // 권한 레벨 → role_seq 매핑
  private Integer permLevelToRoleSeq(Integer permLevel) {
    switch (permLevel) {
      case 2: return 2; // '조회'
      case 3: return 3; // '청취'
      case 4: return 4; // '다운로드'
      default: return 1; // 'NONE'
    }
  }


  @Transactional(readOnly = true)
  public List<GrantDto> listGrantsFor(Integer memberSeq) {
    return permRepo.findByMemberSeq(memberSeq).stream()
        .map(up -> GrantDto.builder()
            .memberSeq(up.getMemberSeq())
            .lineId(up.getLineId())
            .permLevel(up.getPermLevel())
            .build()
        )
        .toList();
  }


}
