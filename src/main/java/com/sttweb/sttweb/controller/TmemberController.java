package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TmemberService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class TmemberController {

  private final TmemberService svc;
  private final HttpSession session;
  private final JwtTokenProvider jwtTokenProvider;

  @PostMapping("/signup")
  public ResponseEntity<String> signup(@RequestBody SignupRequest req) {
    Info me = svc.getMyInfo();
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
    }
    svc.signup(req);
    return ResponseEntity.ok("가입 완료");
  }

  @PostMapping("/login")
  public ResponseEntity<Info> login(@RequestBody LoginRequest req) {
    TmemberEntity user = svc.login(req);
    String token = jwtTokenProvider.createToken(user.getUserId(), user.getUserLevel());
    Info info = Info.builder()
        .memberSeq(user.getMemberSeq())
        .branchSeq(user.getBranchSeq())
        .employeeId(user.getEmployeeId())
        .userId(user.getUserId())
        .userLevel(user.getUserLevel())
        .number(user.getNumber())
        .discd(user.getDiscd())
        .crtime(user.getCrtime())
        .udtime(user.getUdtime())
        .reguserId(user.getReguserId())
        .roleSeq(user.getRoleSeq())
        .token(token)
        .tokenType("Bearer")
        .build();
    session.setAttribute("memberSeq", info.getMemberSeq());
    return ResponseEntity.ok(info);
  }

  @PostMapping("/logout")
  public ResponseEntity<String> logout() {
    svc.logout();
    return ResponseEntity.ok("로그아웃 완료");
  }

  @GetMapping("/me")
  public ResponseEntity<?> getMyInfo(@RequestHeader("Authorization") String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("토큰이 없습니다.");
    }
    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
    }
    String userId = jwtTokenProvider.getUserId(token);
    Info info = svc.getMyInfoByUserId(userId);
    return ResponseEntity.ok(info);
  }

  @PutMapping("/password")
  public ResponseEntity<String> changePassword(@RequestBody PasswordChangeRequest req) {
    Info me = svc.getMyInfo();
    svc.changePassword(me.getMemberSeq(), req);
    return ResponseEntity.ok("비밀번호 변경 완료");
  }

  /** 전체 유저 조회 (관리자만) — Page<Info> 그대로 반환 */
  @GetMapping
  public ResponseEntity<Page<Info>> listAll(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size,
      @RequestHeader("Authorization") String authHeader
  ) {
    // 관리자 검증
    if (!jwtTokenProvider.validateToken(authHeader.substring(7)) ||
        !"0".equals(svc.getMyInfoByUserId(jwtTokenProvider.getUserId(authHeader.substring(7))).getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    PageRequest pr = PageRequest.of(page, size);
    Page<Info> paged = svc.listAllUsers(pr);
    return ResponseEntity.ok(paged);
  }

  @PutMapping("/{id}/status")
  public ResponseEntity<String> changeStatus(
      @PathVariable("id") Integer id,
      @RequestBody StatusChangeRequest req,
      @RequestHeader("Authorization") String authHeader
  ) {
    Info me = svc.getMyInfoByUserId(jwtTokenProvider.getUserId(authHeader.substring(7)));
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
    }
    svc.changeStatus(id, req);
    return ResponseEntity.ok("상태 변경 완료");
  }

}
