// src/main/java/com/sttweb/sttweb/controller/RoleController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.ListResponse;
import com.sttweb.sttweb.dto.TmemberRoleDto;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TmemberRoleService;
import com.sttweb.sttweb.service.TmemberService;
import java.util.Comparator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TmemberRoleController {

  private final TmemberRoleService roleSvc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;

  /**
   * Authorization 헤더 검사 + 관리자(userLevel="0") 권한 체크
   * @return 에러 ResponseEntity or null(통과)
   */
  private ResponseEntity<String> checkAdmin(String header) {
    if (header == null || !header.startsWith("Bearer "))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("토큰이 없습니다.");
    String token = header.substring(7);
    if (!jwtTokenProvider.validateToken(token))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
    String userLevel = memberSvc
        .getMyInfoByUserId(jwtTokenProvider.getUserId(token))
        .getUserLevel();
    if (!"0".equals(userLevel))
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
    return null;
  }

  /** 1) 역할 목록 조회 (모두 허용) */
  @GetMapping("/roles")
  public ResponseEntity<ListResponse<TmemberRoleDto>> listRoles() {
    List<TmemberRoleDto> sorted = roleSvc.listAllRoles().stream()
        .sorted(Comparator.comparing(TmemberRoleDto::getRoleSeq))
        .collect(Collectors.toList());
    return ResponseEntity.ok(new ListResponse<>(sorted.size(), sorted));
  }

  /** 2) 내 권한 조회 (로그인만 하면 OK) */
  @GetMapping("/members/me/role")
  public ResponseEntity<TmemberRoleDto> getMyRole(
      @RequestHeader("Authorization") String header) {
    String token = header.substring(7);
    String userId = jwtTokenProvider.getUserId(token);
    TmemberRoleDto dto = roleSvc.getRoleByUserId(userId);
    return ResponseEntity.ok(dto);
  }

  /** 3) 다른 사용자의 권한 변경 (관리자만) */
  @PutMapping("/members/{memberSeq}/role")
  public ResponseEntity<String> changeUserRole(
      @RequestHeader(value="Authorization", required=false) String header,
      @PathVariable("memberSeq") Integer memberSeq,
      @RequestBody Map<String,Integer> body
  ) {
    ResponseEntity<String> err = checkAdmin(header);
    if (err != null) return err;

    Integer newRole = body.get("roleSeq");
    memberSvc.changeRole(memberSeq, newRole);
    return ResponseEntity.ok("역할 변경 완료");
  }
}
