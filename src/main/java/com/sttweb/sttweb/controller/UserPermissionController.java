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
  private final TmemberService           memberSvc;
  private final JwtTokenProvider         jwtTokenProvider;  // JWT 검증용

  // --- JWT 토큰 추출/검증 + 로그인된 사용자 확인 ---
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

  private Info getCurrentUserFromToken(String authHeader) {
    String token  = extractToken(authHeader);
    String userId = jwtTokenProvider.getUserId(token);
    return memberSvc.getMyInfoByUserId(userId);
  }

  private Info requireLogin(String authHeader) {
    return getCurrentUserFromToken(authHeader);
  }
  // --------------------------------------------------

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

  // granteeUserId 로 권한 레코드만 DTO 로 변환
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
   * hqadmin 같은 granteeUserId 에 대해
   * 모든 회원을 순회하며 permLevel을 체크해서 리턴
   *
   * - GET /api/user-permissions/{granteeUserId}
   * - GET /api/user-permissions?granteeUserId=...
   */
  @LogActivity(type = "role", activity = "조회", contents = "권한조회")
  @GetMapping({"/{granteeUserId}", ""})
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserPermissionViewDto>> viewPermissions(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable(value = "granteeUserId", required = false) String fromPath,
      @RequestParam(value = "granteeUserId", required = false) String fromParam
  ) {
    // 1) 토큰 검증 + AOP 로그 기록용
    Info me = requireLogin(authHeader);

    // 2) path-variable 우선, 없으면 query-param
    String grantee = (fromPath != null) ? fromPath : fromParam;
    if (grantee == null || grantee.isBlank()) {
      return ResponseEntity.badRequest().build();
    }

    // 3) 실제 권한 부여된 대상 리스트 반환
    List<UserPermissionViewDto> list = listByGrantee(grantee);
    return ResponseEntity.ok(list);
  }

  @GetMapping({"/all/{granteeUserId}", "/all"})
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserPermissionViewDto>> viewAllWithDefaults(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable(value = "granteeUserId", required = false) String fromPath,
      @RequestParam(value = "granteeUserId", required = false) String fromParam
  ) {
    Info me = requireLogin(authHeader);
    String grantee = (fromPath != null) ? fromPath : fromParam;
    if (grantee == null || grantee.isBlank()) {
      return ResponseEntity.badRequest().build();
    }

    // 전체 회원 조회 (자신 제외)
    List<Info> allMembers = memberSvc.getAllMembers();
    return ResponseEntity.ok(
        allMembers.stream()
            .filter(m -> !m.getUserId().equals(grantee))
            .map(m -> {
              // 권한 레코드가 있으면 레벨 읽어오고, 없으면 1(NONE)
              int level = repo.findByGranteeUserIdAndTargetUserId(grantee, m.getUserId())
                  .map(UserPermission::getPermLevel)
                  .orElse(1);
              return new UserPermissionViewDto(
                  m.getUserId(),
                  level >= 2,
                  level >= 3,
                  level >= 4
              );
            })
            .collect(Collectors.toList())
    );
  }

}
