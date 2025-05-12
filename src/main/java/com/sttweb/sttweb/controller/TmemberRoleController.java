// src/main/java/com/sttweb/sttweb/controller/TmemberRoleController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.ListResponse;
import com.sttweb.sttweb.dto.TmemberRoleDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.exception.UnauthorizedException;
import com.sttweb.sttweb.exception.ForbiddenException;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TmemberRoleService;
import com.sttweb.sttweb.service.TmemberService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TmemberRoleController {

  private final TmemberRoleService roleSvc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;

  private void checkAdmin(String header) {
    if (header == null || !header.startsWith("Bearer "))
      throw new UnauthorizedException("토큰이 없습니다.");
    String token = header.substring(7);
    if (!jwtTokenProvider.validateToken(token))
      throw new UnauthorizedException("유효하지 않은 토큰입니다.");
    String userLevel = memberSvc
        .getMyInfoByUserId(jwtTokenProvider.getUserId(token))
        .getUserLevel();
    if (!"0".equals(userLevel))
      throw new ForbiddenException("권한이 없습니다.");
  }

  /** 1) 역할 목록 조회 (모두 허용) */
  @LogActivity(type = "role", activity = "목록조회")
  @GetMapping("/roles")
  public ResponseEntity<ListResponse<TmemberRoleDto>> listRoles() {
    List<TmemberRoleDto> sorted = roleSvc.listAllRoles().stream()
        .sorted(Comparator.comparing(TmemberRoleDto::getRoleSeq))
        .collect(Collectors.toList());
    ListResponse<TmemberRoleDto> resp = new ListResponse<>(
        sorted.size(), sorted, 0, sorted.size(), 1);
    return ResponseEntity.ok(resp);
  }

  /** 2) 내 권한 조회 (로그인만 하면 OK) */
  @LogActivity(type = "role", activity = "내역할조회")
  @GetMapping("/members/me/role")
  public ResponseEntity<TmemberRoleDto> getMyRole(
      @RequestHeader("Authorization") String header
  ) {
    if (!header.startsWith("Bearer "))
      throw new UnauthorizedException("토큰이 없습니다.");
    String token = header.substring(7);
    if (!jwtTokenProvider.validateToken(token))
      throw new UnauthorizedException("유효하지 않은 토큰입니다.");
    String userId = jwtTokenProvider.getUserId(token);
    TmemberRoleDto dto = roleSvc.getRoleByUserId(userId);
    return ResponseEntity.ok(dto);
  }

  /** 3) 다른 사용자의 권한 변경 (관리자만) */
  @LogActivity(
      type     = "role",
      activity = "변경",
      contents = "'memberSeq=' + #memberSeq + ',roleSeq=' + #body['roleSeq']"
  )
  @PutMapping("/members/{memberSeq}/role")
  public ResponseEntity<String> changeUserRole(
      @RequestHeader(value="Authorization", required=false) String header,
      @PathVariable("memberSeq") Integer memberSeq,
      @RequestBody Map<String,Integer> body
  ) {
    checkAdmin(header);
    Integer newRole = body.get("roleSeq");
    memberSvc.changeRole(memberSeq, newRole);
    return ResponseEntity.ok("역할 변경 완료");
  }
}
