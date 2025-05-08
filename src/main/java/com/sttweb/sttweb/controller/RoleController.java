// src/main/java/com/sttweb/sttweb/controller/RoleController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberRoleDto;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.RoleService;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoleController {

  private final RoleService roleSvc;
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
    String userId = jwtTokenProvider.getUserId(token);
    // 세션 대신 토큰을 기반으로 pull 해서...
    // UserService 에서 정보 꺼내오도록 구현했다고 가정
    // 아래는 가독성 위해 간단화
    String userLevel = memberSvc.getMyInfoByUserId(userId).getUserLevel();
    if (!"0".equals(userLevel))
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
    return null;
  }

  /** 1) 역할 목록 조회 (모두 허용) */
  @GetMapping("/roles")
  public ResponseEntity<List<TmemberRoleDto>> listRoles() {
    return ResponseEntity.ok(roleSvc.listAllRoles());
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
