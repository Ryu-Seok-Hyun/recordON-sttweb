package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TmemberRoleDto;
import java.util.List;

public interface RoleService {
  /** DB 에 저장된 모든 역할 목록 조회 */
  List<TmemberRoleDto> listAllRoles();

  /** userId 로 조회한 사용자의 역할 정보 조회 */
  TmemberRoleDto getRoleByUserId(String userId);
}