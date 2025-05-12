package com.sttweb.sttweb.service;


import com.sttweb.sttweb.dto.TmemberRoleDto;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.entity.TmemberRoleEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.repository.TmemberRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TmemberRoleServiceImpl implements TmemberRoleService {
  private final TmemberRoleRepository roleRepo;
  private final TmemberRepository memberRepo;

  @Override
  public List<TmemberRoleDto> listAllRoles() {
    return roleRepo.findAll()
        .stream()
        .map(e -> new TmemberRoleDto(e.getRoleSeq(), e.getRoleCode(), e.getDescription()))
        .collect(Collectors.toList());
  }

  @Override
  public TmemberRoleDto getRoleByUserId(String userId) {
    TmemberEntity user = memberRepo.findByUserId(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    TmemberRoleEntity role = roleRepo.findById(user.getRoleSeq())
        .orElseThrow(() -> new IllegalStateException("권한 정보를 찾을 수 없습니다."));
    return new TmemberRoleDto(role.getRoleSeq(), role.getRoleCode(), role.getDescription());
  }
}