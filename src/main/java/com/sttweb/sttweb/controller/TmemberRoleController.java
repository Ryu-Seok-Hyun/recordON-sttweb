// src/main/java/com/sttweb/sttweb/controller/TmemberRoleController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.ListResponse;
import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberRoleDto;
import com.sttweb.sttweb.exception.ForbiddenException;
import com.sttweb.sttweb.exception.UnauthorizedException;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberRoleService;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
  private final TbranchService branchSvc;
  private final JwtTokenProvider jwtTokenProvider;

  /**
   * Authorization 헤더에서 Bearer 토큰만 꺼내고 유효성 검사까지 수행.
   * 없거나 잘못된 경우 UnauthorizedException 던짐.
   */
  private String extractToken(String header) {
    if (header == null || !header.startsWith("Bearer ")) {
      throw new UnauthorizedException("토큰이 없습니다.");
    }
    String token = header.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      throw new UnauthorizedException("유효하지 않은 토큰입니다.");
    }
    return token;
  }

  /** 1) 역할 목록 조회 (모두 허용) */
  @LogActivity(type = "role", activity = "'조회'", contents = "역할 목록 조회")
  @GetMapping("/roles")
  public ResponseEntity<ListResponse<TmemberRoleDto>> listRoles() {
    List<TmemberRoleDto> sorted = roleSvc.listAllRoles().stream()
        .sorted(Comparator.comparing(TmemberRoleDto::getRoleSeq))
        .collect(Collectors.toList());
    ListResponse<TmemberRoleDto> resp =
        new ListResponse<>(sorted.size(), sorted, 0, sorted.size(), 1);
    return ResponseEntity.ok(resp);
  }

  /** 2) 내 권한 조회 (로그인만 하면 OK) */
  @LogActivity(type = "role", activity = "'조회'", contents = "내 역할 조회")
  @GetMapping("/members/me/role")
  public ResponseEntity<TmemberRoleDto> getMyRole(
      @RequestHeader("Authorization") String header
  ) {
    String token = extractToken(header);
    String userId = jwtTokenProvider.getUserId(token);
    TmemberRoleDto dto = roleSvc.getRoleByUserId(userId);
    return ResponseEntity.ok(dto);
  }

  /**
   * 3a) 다른 사용자의 권한 변경 (memberSeq 기준)
   *     - 본사 관리자(hqYn == "0") 이거나
   *     - 지사 관리자(자기 지점 소속 사용자만)
   */
  @LogActivity(
      type     = "role",
      activity = "수정",
      // 스프링 EL 로 principal.name(로그인ID) 과 파라미터 memberSeq 출력
      contents = "다른 사용자 권한 변경: #{#principal.name} → memberSeq:#{#targetMemberSeq}"
  )
  @PutMapping("/members/{memberSeq}/role")
  public ResponseEntity<String> changeUserRoleBySeq(
      @RequestHeader(value="Authorization", required=false) String header,
      @PathVariable("memberSeq") Integer targetMemberSeq,
      @RequestBody Map<String,Integer> body
  ) {
    return doChangeRole(header, targetMemberSeq, body);
  }

  /**
   * 3b) 다른 사용자의 권한 변경 (userId 기준)
   *     - userId → memberSeq 변환 후 동일 로직 사용
   */
  @LogActivity(
      type     = "role",
      activity = "수정",
      contents = "다른 사용자 권한 변경: #{#principal.name} → userId:#{#targetUserId}"
  )
  @PutMapping("/members/user/{userId}/role")
  public ResponseEntity<String> changeUserRoleByUserId(
      @RequestHeader(value="Authorization", required=false) String header,
      @PathVariable("userId") String targetUserId,
      @RequestBody Map<String,Integer> body
  ) {
    // userId 로 대상 회원정보 조회
    Info target = memberSvc.getMyInfoByUserId(targetUserId);
    Integer targetMemberSeq = target.getMemberSeq();
    return doChangeRole(header, targetMemberSeq, body);
  }

  /**
   * 4) 공통 권한 변경 처리:
   *    1) 토큰 검사
   *    2) 본사/지사 관리자 권한 체크
   *    3) memberSvc.changeRole 호출
   */
  private ResponseEntity<String> doChangeRole(
      String header,
      Integer targetMemberSeq,
      Map<String,Integer> body
  ) {
    // 1) 토큰 유효성 검사
    String token = extractToken(header);
    String myUserId = jwtTokenProvider.getUserId(token);

    // 2) 내 정보 조회
    Info me = memberSvc.getMyInfoByUserId(myUserId);
    Integer myBranchSeq = me.getBranchSeq();
    if (myBranchSeq == null) {
      throw new ForbiddenException("소속 지점 정보가 없습니다.");
    }

    // 3) 내 지점 정보 조회
    TbranchDto myBranch = branchSvc.findById(myBranchSeq);

    // 4) 대상 정보 조회
    Info target = memberSvc.getMyInfoByMemberSeq(targetMemberSeq);
    Integer targetBranchSeq = target.getBranchSeq();

    // 5) 권한 검사
    //    - 본사 관리자(hqYn == "0") 이면 무조건 OK
    //    - 지사 관리자면 같은 지점 대상만 허용
    if (!"0".equals(myBranch.getHqYn())) {
      if (targetBranchSeq == null || !myBranchSeq.equals(targetBranchSeq)) {
        throw new ForbiddenException("지사 관리자 권한이 없습니다.");
      }
    }

    // 6) 실제 변경
    Integer newRoleSeq = body.get("roleSeq");
    if (newRoleSeq == null) {
      throw new IllegalArgumentException("roleSeq를 지정해주세요.");
    }
    memberSvc.changeRole(targetMemberSeq, newRoleSeq);

    return ResponseEntity.ok("역할 변경 완료");
  }
}
