package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.ListResponse;
import com.sttweb.sttweb.dto.TmemberDto;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TmemberService;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class TmemberController {

  private final TmemberService svc;
  private final HttpSession session;
  private final JwtTokenProvider jwtTokenProvider;

  // 회원등록(관리자가 아니면 등록 못함)
  @PostMapping("/signup")
  public ResponseEntity<String> signup(@RequestBody SignupRequest req) {
    Info me = svc.getMyInfo();

    // 2) userLevel이 "0"(관리자)가 아니면 접근 차단
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity
          .status(HttpStatus.FORBIDDEN)
          .body("권한이 없습니다.");
    }
    svc.signup(req);
    return ResponseEntity.ok("가입 완료");
  }


  @PostMapping("/login")
  public ResponseEntity<Info> login(@RequestBody LoginRequest req) {
    // 1) 아이디/비밀번호 체크
    TmemberEntity user = svc.login(req);

    // 2) 토큰 생성
    String token = jwtTokenProvider.createToken(user.getUserId(), user.getUserLevel());

    // 3) Entity → DTO
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
        .role_seq(user.getRoleSeq())
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

    // 3) 토큰에서 userId 꺼내기
    String userId = jwtTokenProvider.getUserId(token);

    // 4) userId 로 서비스 호출
    TmemberDto.Info info = svc.getMyInfoByUserId(userId);

    return ResponseEntity.ok(info);
  }


  // 비밀번호 변경
  @PutMapping("/password")
  public ResponseEntity<String> changePassword(@RequestBody PasswordChangeRequest req) {
    // 1) 로그인한 내 정보 조회
    Info me = svc.getMyInfo();  // 세션이나 JWT 필터를 통해 꺼낸 내 정보

    // 2) 서비스에 내 memberSeq 와 요청 객체를 넘겨서 비밀번호 변경
    svc.changePassword(me.getMemberSeq(), req);

    return ResponseEntity.ok("비밀번호 변경 완료");
  }

  /** 전체유저 조회(관리자만) */
  @GetMapping
  public ResponseEntity<?> listAll() {
    Info me = svc.getMyInfo();
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
    }

    List<Info> all = svc.listAllUsers().stream()
        .sorted(Comparator.comparing(Info::getMemberSeq))
        .collect(Collectors.toList());

    return ResponseEntity.ok(new ListResponse<>(all.size(), all));
  }

  /** 사용자 활성/비활성 */
  @PutMapping("/{id}/status")
  public ResponseEntity<String> changeStatus(
      @PathVariable("id") Integer id,             // ← 여기
      @RequestBody StatusChangeRequest req) {

    // 1) 세션에 저장된 내 정보 조회
    Info me = svc.getMyInfo();

    // 2) 관리자(userLevel == "0") 검증
    if (!"0".equals(me.getUserLevel())) {
      // 403 Forbidden
      return ResponseEntity
          .status(HttpStatus.FORBIDDEN)
          .body("권한이 없습니다.");
    }

    // 3) 권한 통과 시 실제 상태 변경
    svc.changeStatus(id, req);
    return ResponseEntity.ok("상태 변경 완료");
  }

}
