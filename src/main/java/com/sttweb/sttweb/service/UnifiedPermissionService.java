package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.MemberLinePermDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UnifiedPermissionService {
  private final UserPermissionRepository userPermRepo;

  public List<Integer> getAllPermittedLineIds(Integer memberSeq) {
    Set<Integer> lineIds = new LinkedHashSet<>();
    List<UserPermission> linePerms = userPermRepo.findByMemberSeq(memberSeq);
    for (UserPermission perm : linePerms) {
      lineIds.add(perm.getLineId());
    }
    return new ArrayList<>(lineIds);
  }


  /**
   * 번호 정규화 (3자리 → 4자리)
   */
  private String normalizeToFourDigit(String number) {
    if (number == null || number.trim().isEmpty()) {
      return null;
    }
    String trimmed = number.trim();
    if (trimmed.length() == 4) {
      return trimmed;
    }
    if (trimmed.length() == 3) {
      return "0" + trimmed;
    }
    return trimmed;
  }
}
