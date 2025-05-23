// src/main/java/com/sttweb/sttweb/controller/UserPermissionController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.UserPermissionViewDto;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user-permissions")
@RequiredArgsConstructor
public class UserPermissionController {

  private final UserPermissionRepository repo;
  private final TmemberService memberSvc;

  @LogActivity(type = "role", activity = "부여", contents = "권한부여")
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> grant(@RequestBody GrantDto dto) {
    UserPermission up = repo
        .findByGranteeUserIdAndTargetUserId(dto.getGranteeUserId(), dto.getTargetUserId())
        .orElseGet(UserPermission::new);

    up.setGranteeUserId(dto.getGranteeUserId());
    up.setTargetUserId(dto.getTargetUserId());
    up.setPermLevel(dto.getPermLevel());
    repo.save(up);

    return ResponseEntity.ok().build();
  }

  @LogActivity(type = "role", activity = "회수", contents = "권한회수")
  @DeleteMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> revoke(@RequestBody GrantDto dto) {
    repo.deleteByGranteeUserIdAndTargetUserId(dto.getGranteeUserId(), dto.getTargetUserId());
    return ResponseEntity.noContent().build();
  }

  // 공통 로직: granteeUserId에 부여된 권한만 뽑아서 DTO 리스트 생성
  private List<UserPermissionViewDto> listByGrantee(String granteeUserId) {
    return repo.findByGranteeUserId(granteeUserId).stream()
        .map(up -> new UserPermissionViewDto(
            up.getTargetUserId(),
            up.getPermLevel() >= 2,
            up.getPermLevel() >= 3,
            up.getPermLevel() >= 4
        ))
        .collect(Collectors.toList());
  }

  /**
   * 1) PathVariable 방식
   *    GET /api/user-permissions/{granteeUserId}
   * 2) QueryParam 방식
   *    GET /api/user-permissions?granteeUserId=...
   * 두 방식을 하나의 메서드로 처리합니다.
   */
  @LogActivity(type = "role", activity = "조회", contents = "권한조회")
  @GetMapping({"/{granteeUserId}", ""})
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserPermissionViewDto>> viewPermissions(
      @PathVariable(value = "granteeUserId", required = false) String fromPath,
      @RequestParam(value = "granteeUserId", required = false) String fromParam
  ) {
    // PathVariable이 우선, 없으면 QueryParam 사용
    String granteeUserId = (fromPath != null) ? fromPath : fromParam;
    if (granteeUserId == null || granteeUserId.isBlank()) {
      // 파라미터가 하나도 없으면 빈 리스트 응답
      return ResponseEntity.ok(List.of());
    }
    return ResponseEntity.ok(listByGrantee(granteeUserId));
  }
}
