// src/main/java/com/sttweb/sttweb/controller/UserPermissionController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.TlineDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.UserPermissionViewDto;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.service.PermissionService;
import com.sttweb.sttweb.service.TlineService;
import com.sttweb.sttweb.service.TmemberService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/user-permissions")
@RequiredArgsConstructor
public class UserPermissionController {
  private final PermissionService permissionService;
  private final UserPermissionRepository repo;

  // 권한 부여
  @PostMapping
  public ResponseEntity<Void> grant(@RequestBody GrantDto dto) {
    permissionService.grantAndSyncLinePerm(dto);
    return ResponseEntity.ok().build();
  }

  // 권한 회수
  @DeleteMapping
  public ResponseEntity<Void> revoke(@RequestBody GrantDto dto) {
    permissionService.revokeAndSyncLinePerm(dto.getMemberSeq(), dto.getLineId());
    return ResponseEntity.noContent().build();
  }

  // 사용자의 권한 목록 조회
  @GetMapping("/{memberSeq}")
  public ResponseEntity<List<GrantDto>> listPermissions(@PathVariable Integer memberSeq) {
    var result = repo.findByMemberSeq(memberSeq).stream()
        .map(up -> GrantDto.builder()
            .memberSeq(up.getMemberSeq())
            .lineId(up.getLineId())
            .permLevel(up.getPermLevel())
            .build())
        .collect(Collectors.toList());
    return ResponseEntity.ok(result);
  }
}
