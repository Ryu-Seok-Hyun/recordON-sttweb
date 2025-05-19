package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.PermissionService;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

// 회원관리파트
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class TmemberController {

  private final TmemberService svc;
  private final JwtTokenProvider jwtTokenProvider;
  private final PermissionService permissionService;


  /** 1) 토큰 유효성 검사 */
  private ResponseEntity<String> checkToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("토큰이 없습니다.");
    }
    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
    }
    return null;
  }

  /** 2) 토큰에서 userId 꺼내서 Info 조회 */
  private Info getMeFromToken(String authHeader) {
    String token = authHeader.substring(7);
    String userId = jwtTokenProvider.getUserId(token);
    return svc.getMyInfoByUserId(userId);
  }

  /** 3) 관리자(userLevel == "0") 여부 체크 */
  private ResponseEntity<String> checkAdmin(String authHeader) {
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;
    Info me = getMeFromToken(authHeader);
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자만 접근 가능합니다.");
    }
    return null;
  }

  /** 회원가입 (관리자만) */
  @LogActivity(type="member", activity="등록", contents="사용자 등록")
  @PostMapping("/signup")
  public ResponseEntity<String> signup(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @Valid @RequestBody SignupRequest req
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;

    Info me = getMeFromToken(authHeader);
    svc.signupWithGrants(req, me.getMemberSeq(), me.getUserId());
    return ResponseEntity.ok("가입 및 권한부여 완료");
  }



  /** 로그인 */
  @LogActivity(type = "member", activity = "로그인")
  @PostMapping("/login")
  public ResponseEntity<Info> login(@Valid @RequestBody LoginRequest req) {
    TmemberEntity user = svc.login(req);
    String token = jwtTokenProvider.createToken(user.getUserId(), user.getUserLevel());

    Info info = Info.fromEntity(user);
    // 로그인 시에도 branchName 채워 주기
    if (info.getBranchSeq() != null) {
      // branchSvc를 직접 쓰지 않고, 서비스에서 getMyInfoByUserId를 쓰도록 해도 됩니다
      info.setBranchName(svc.getMyInfoByUserId(user.getUserId()).getBranchName());
    }
    info.setToken(token);
    info.setTokenType("Bearer");
    return ResponseEntity.ok(info);
  }

  /** 로그아웃 */
  @LogActivity(type = "member", activity = "로그아웃")
  @PostMapping("/logout")
  public ResponseEntity<String> logout() {
    svc.logout();
    return ResponseEntity.ok("로그아웃 완료");
  }

  /** 내 정보 조회 */
  @LogActivity(type = "member", activity = "조회", contents = "내 정보 조회")
  @GetMapping("/me")
  public ResponseEntity<?> getMyInfo(
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;
    Info info = getMeFromToken(authHeader);
    return ResponseEntity.ok(info);
  }

  /** 비밀번호 변경 */
  @LogActivity(type = "member", activity = "수정", contents = "PW변경")
  @PutMapping("/password")
  public ResponseEntity<String> changePassword(
      @Valid @RequestBody PasswordChangeRequest req,
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;
    Info me = getMeFromToken(authHeader);
    svc.changePassword(me.getMemberSeq(), req);
    return ResponseEntity.ok("비밀번호 변경 완료");
  }

  /** ★ 전체 조회 or 키워드 검색 (관리자만) */
  @LogActivity(type="member", activity="조회", contents="전체 유저 조회/검색")
  @GetMapping
  public ResponseEntity<Page<Info>> listOrSearchUsers(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestParam(name="keyword", required=false) String keyword,
      @RequestParam(name="page",    defaultValue="0")  int page,
      @RequestParam(name="size",    defaultValue="10") int size
  ) {
    // 1) 401/403 처리 (토큰 유효 + 관리자 여부)
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return ResponseEntity
        .status(err.getStatusCode()).body(null);

    // 2) 페이징 객체 생성
    Pageable pr = PageRequest.of(page, size);

    // 3) 키워드 있으면 searchUsers, 없으면 listAllUsers
    Page<Info> result;
    if (keyword != null && !keyword.isBlank()) {
      result = svc.searchUsers(keyword.trim(), pr);
    } else {
      result = svc.listAllUsers(pr);
    }

    return ResponseEntity.ok(result);
  }

  /** 상태 변경 (관리자만) */
  @LogActivity(type = "member", activity = "수정", contents = "상태 변경")
  @PutMapping("/{id}/status")
  public ResponseEntity<String> changeStatus(
      @PathVariable("id") Integer id,
      @Valid @RequestBody StatusChangeRequest req,
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;
    svc.changeStatus(id, req);
    return ResponseEntity.ok("상태 변경 완료");
  }
}

