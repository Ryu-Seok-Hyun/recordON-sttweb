// src/main/java/com/sttweb/sttweb/controller/UserPermissionController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.UserPermissionViewDto;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.UserPermissionRepository;
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

  private final UserPermissionRepository repo;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;

  private String extractToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
    }
    String token = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    }
    return token;
  }

  private Info requireLogin(String authHeader) {
    String token = extractToken(authHeader);
    String userId = jwtTokenProvider.getUserId(token);
    return memberSvc.getMyInfoByUserId(userId);
  }

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

  private List<UserPermissionViewDto> listByGrantee(String grantee) {
    return repo.findByGranteeUserId(grantee).stream()
        .map(up -> UserPermissionViewDto.builder()
            .userId(up.getTargetUserId())
            .canRead(up.getPermLevel() >= 2)
            .canListen(up.getPermLevel() >= 3)
            .canDownload(up.getPermLevel() >= 4)
            .crtime(up.getCrtime())        // ← 엔티티의 getCrtime() 사용
            .build()
        ).collect(Collectors.toList());
  }

  @LogActivity(type = "role", activity = "조회", contents = "권한조회")
  @GetMapping({"/{granteeUserId}", ""})
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserPermissionViewDto>> viewPermissions(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable(value = "granteeUserId", required = false) String p,
      @RequestParam(value = "granteeUserId", required = false) String q
  ) {
    requireLogin(authHeader);
    String grantee = (p != null ? p : q);
    if (grantee == null || grantee.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(listByGrantee(grantee));
  }

  @GetMapping({"/all/{granteeUserId}", "/all"})
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserPermissionViewDto>> viewAllWithDefaults(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable(value = "granteeUserId", required = false) String p,
      @RequestParam(value = "granteeUserId", required = false) String q
  ) {
    requireLogin(authHeader);
    String grantee = (p != null ? p : q);
    if (grantee == null || grantee.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    List<Info> all = memberSvc.getAllMembers();
    return ResponseEntity.ok(
        all.stream()
            .filter(m -> !m.getUserId().equals(grantee))
            .map(m -> {
              int lvl = repo.findByGranteeUserIdAndTargetUserId(grantee, m.getUserId())
                  .map(UserPermission::getPermLevel).orElse(1);
              LocalDateTime ct = repo.findByGranteeUserIdAndTargetUserId(grantee, m.getUserId())
                  .map(UserPermission::getCrtime).orElse(null);
              return UserPermissionViewDto.builder()
                  .userId(m.getUserId())
                  .canRead(lvl >= 2)
                  .canListen(lvl >= 3)
                  .canDownload(lvl >= 4)
                  .crtime(ct)
                  .build();
            }).collect(Collectors.toList())
    );
  }
}
