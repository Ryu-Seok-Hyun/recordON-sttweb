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

// 다른 사람에 대한 권한 파트
@RestController
@RequestMapping("/api/user-permissions")
@RequiredArgsConstructor
public class UserPermissionController {

  private final UserPermissionRepository repo;
  private final TmemberService memberSvc;

  @LogActivity(
      type     = "role",
      activity = "부여",
      contents = "권한부여"
  )
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> grant(@RequestBody GrantDto dto) {
    // user_id 기반으로 권한 엔티티 조회 또는 새로 생성
    UserPermission up = repo
        .findByGranteeUserIdAndTargetUserId(dto.getGranteeUserId(), dto.getTargetUserId())
        .orElseGet(UserPermission::new);

    up.setGranteeUserId(dto.getGranteeUserId());
    up.setTargetUserId(dto.getTargetUserId());
    up.setPermLevel(dto.getPermLevel());
    repo.save(up);

    return ResponseEntity.ok().build();
  }

  @LogActivity(
      type     = "role",
      activity = "회수",
      contents = "권한회수"
  )
  @DeleteMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> revoke(@RequestBody GrantDto dto) {
    // user_id 기반으로 바로 삭제
    repo.deleteByGranteeUserIdAndTargetUserId(dto.getGranteeUserId(), dto.getTargetUserId());
    return ResponseEntity.noContent().build();
  }

  @LogActivity(
      type     = "role",
      activity = "조회",
      contents = "권한조회"
  )
  @GetMapping("/{granteeUserId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserPermissionViewDto>> viewPermissions(
      @PathVariable("granteeUserId") String granteeUserId
  ) {
    // 전체 사용자 목록을 돌며, user_id 기반으로 권한 레벨 조회
    List<UserPermissionViewDto> list = memberSvc.getAllMembers().stream()
        .map(m -> {
          int level = repo
              .findByGranteeUserIdAndTargetUserId(granteeUserId, m.getUserId())
              .map(UserPermission::getPermLevel)
              .orElse(1);
          return new UserPermissionViewDto(
              m.getUserId(),
              level >= 2,
              level >= 3,
              level >= 4
          );
        })
        .collect(Collectors.toList());

    return ResponseEntity.ok(list);
  }
}
