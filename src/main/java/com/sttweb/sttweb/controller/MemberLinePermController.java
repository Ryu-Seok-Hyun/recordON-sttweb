package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.GrantLineDto;
import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.MemberLinePermDto;
import com.sttweb.sttweb.dto.UserPermissionViewDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.MemberLinePermService;
import com.sttweb.sttweb.service.PermissionService;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/perm")
@RequiredArgsConstructor
public class MemberLinePermController {

  private final MemberLinePermService service;
  private final PermissionService      permService;
  private final TmemberService         memberSvc;
  private final JwtTokenProvider       jwtTokenProvider;

  private String extractToken(String authHeader) {
    if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
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
    return memberSvc.getMyInfoByUserId(jwtTokenProvider.getUserId(token));
  }

  // 0) 전체 회선 매핑 조회
  @GetMapping
  public ResponseEntity<List<MemberLinePermDto>> getAllMappings(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
  ) {
    requireLogin(authHeader);
    return ResponseEntity.ok(service.getAllMappings());
  }

  // 1) 회원별 실제로 부여된 회선 매핑 조회
  @GetMapping("/member/{memberSeq}")
  public ResponseEntity<List<MemberLinePermDto>> getByMember(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq
  ) {
    requireLogin(authHeader);
    return ResponseEntity.ok(service.getPermissionsByMember(memberSeq));
  }

  // 1-1) 회원별 전체 회선 + 권한 유무 조회
  @GetMapping("/member/{memberSeq}/all-lines")
  public ResponseEntity<List<MemberLinePermDto>> getAllLinesWithPerm(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq
  ) {
    requireLogin(authHeader);
    return ResponseEntity.ok(service.getAllLinesWithPerm(memberSeq));
  }

  // 1-2) 모든 회원 × 전체 회선 조합 조회
  @GetMapping("/all-members-lines")
  public ResponseEntity<List<MemberLinePermDto>> getAllMembersAllLinesPerm(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
  ) {
    requireLogin(authHeader);
    return ResponseEntity.ok(service.getAllMembersAllLinesPerm());
  }

  // 2) 회선별 매핑 조회
  @GetMapping("/line/{lineId}")
  public ResponseEntity<List<MemberLinePermDto>> getByLine(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("lineId") Integer lineId
  ) {
    requireLogin(authHeader);
    return ResponseEntity.ok(service.getPermissionsByLine(lineId));
  }

  // 3) 회선 권한 부여 (본사0, 지사1만)
  @LogActivity(type = "perm", activity = "부여", contents = "회선 권한 부여")
  @PostMapping("/grant-line")
  public ResponseEntity<String> grantLinePermission(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody GrantLineDto dto
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
    boolean granted = service.grantLinePermission(
        dto.getMemberSeq(), dto.getLineId(), dto.getRoleSeq()
    );
    return granted
        ? ResponseEntity.ok("회선 권한이 부여되었습니다.")
        : ResponseEntity.badRequest().body("잘못된 요청입니다.");
  }

  // 4) 회선 권한 회수 (본사0, 지사1만)
  @LogActivity(type = "perm", activity = "회수", contents = "회선 권한 회수")
  @DeleteMapping("/revoke-line")
  public ResponseEntity<String> revokeLinePermission(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody GrantLineDto dto
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
    boolean revoked = service.revokeLinePermission(
        dto.getMemberSeq(), dto.getLineId()
    );
    return revoked
        ? ResponseEntity.ok("회선 권한이 회수되었습니다.")
        : ResponseEntity.badRequest().body("해당 권한 매핑이 존재하지 않습니다.");
  }

  // 5) 사용자 간 녹취조회 권한 조회
  @GetMapping("/user/{granteeUserId}")
  public ResponseEntity<List<UserPermissionViewDto>> viewUserPermissions(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("granteeUserId") String granteeUserId
  ) {
    requireLogin(authHeader);
    List<UserPermissionViewDto> out = permService.listGrantsFor(granteeUserId).stream()
        .map(g -> UserPermissionViewDto.builder()
            .userId(g.getTargetUserId())
            .canRead(g.getPermLevel() >= 2)
            .canListen(g.getPermLevel() >= 3)
            .canDownload(g.getPermLevel() >= 4)
            .build()
        ).toList();
    return ResponseEntity.ok(out);
  }

  // 6) 사용자 간 녹취조회 권한 부여/회수
  @LogActivity(type = "perm", activity = "부여", contents = "사용자 권한 부여")
  @PostMapping("/grant-user")
  public ResponseEntity<Void> grantUserPermission(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody GrantDto dto
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
    permService.grant(dto);
    return ResponseEntity.ok().build();
  }

  @LogActivity(type = "perm", activity = "회수", contents = "사용자 권한 회수")
  @DeleteMapping("/revoke-user")
  public ResponseEntity<Void> revokeUserPermission(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody GrantDto dto
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
    permService.revoke(dto.getGranteeUserId(), dto.getTargetUserId());
    return ResponseEntity.noContent().build();
  }

}
