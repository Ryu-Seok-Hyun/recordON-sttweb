package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import com.sttweb.sttweb.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private final TrecordService recordSvc;
  private final TmemberService memberSvc;
  private final RoleService roleSvc;
  private final JwtTokenProvider jwtTokenProvider;

  /**
   * 인증·권한 정보를 담는 DTO
   */
  private static class AuthInfo {
    final String userId;
    final Integer memberSeq;
    final Integer roleSeq;    // 1=NONE,2=READ,3=LISTEN,4=DOWNLOAD
    final String userLevel;   // "0"=관리자, "1"=일반
    final String myNumber;    // 내선번호

    AuthInfo(String userId, Integer memberSeq, String userLevel, Integer roleSeq, String myNumber) {
      this.userId    = userId;
      this.memberSeq = memberSeq;
      this.userLevel = userLevel;
      this.roleSeq   = roleSeq;
      this.myNumber  = myNumber;
    }
  }

  /**
   * 401: 토큰 유무·유효성 검사
   */
  private AuthInfo authenticate(
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new UnauthorizedException("토큰이 없습니다.");
    }
    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      throw new UnauthorizedException("유효하지 않은 토큰입니다.");
    }
    String userId = jwtTokenProvider.getUserId(token);
    Info me = memberSvc.getMyInfoByUserId(userId);
    Integer roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    return new AuthInfo(
        userId,
        me.getMemberSeq(),
        me.getUserLevel(),
        roleSeq,
        me.getNumber()
    );
  }

  /**
   * 403: 최소 권한 단계 체크
   */
  private void requireMinimumRole(AuthInfo ai, int minRole) {
    if (ai.roleSeq < minRole) {
      throw new ForbiddenException("권한이 없습니다.");
    }
  }

  /**
   * 403: 관리자 전용
   */
  private void requireAdmin(AuthInfo ai) {
    if (!"0".equals(ai.userLevel)) {
      throw new ForbiddenException("관리자 권한이 필요합니다.");
    }
  }

  /**
   * 1) 전체 녹취 조회 (READ 이상)
   */
  @GetMapping
  public ResponseEntity<List<TrecordDto>> listAll(
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireMinimumRole(ai, 2);

    List<TrecordDto> all = recordSvc.findAll();
    // READ(2) 혹은 LISTEN(3) 권한이면 자신의 내선번호 통화만
    if (ai.roleSeq == 2 || ai.roleSeq == 3) {
      all = all.stream()
          .filter(r -> ai.myNumber.equals(r.getNumber1()))
          .collect(Collectors.toList());
    }
    return ResponseEntity.ok(all);
  }

  /**
   * 2) 번호로 검색 (READ 이상)
   */
  @GetMapping("/search")
  public ResponseEntity<List<TrecordDto>> searchByNumber(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestParam(value="number1", required=false) String number1,
      @RequestParam(value="number2", required=false) String number2
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireMinimumRole(ai, 2);

    List<TrecordDto> results = recordSvc.searchByNumber(number1, number2);
    if (ai.roleSeq == 2 || ai.roleSeq == 3) {
      results = results.stream()
          .filter(r -> ai.myNumber.equals(r.getNumber1()))
          .collect(Collectors.toList());
    }
    return ResponseEntity.ok(results);
  }

  /**
   * 3) 단건 조회 (READ 이상)
   */
  @GetMapping("/{id}")
  public ResponseEntity<TrecordDto> getById(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireMinimumRole(ai, 2);

    TrecordDto dto = recordSvc.findById(id);
    if ((ai.roleSeq == 2 || ai.roleSeq == 3)
        && !ai.myNumber.equals(dto.getNumber1())) {
      throw new ForbiddenException("권한이 없습니다.");
    }
    return ResponseEntity.ok(dto);
  }

  /**
   * 4) 녹취 등록 (관리자만)
   */
  @PostMapping
  public ResponseEntity<TrecordDto> create(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestBody TrecordDto dto
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireAdmin(ai);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(recordSvc.create(dto));
  }

  /**
   * 5) 녹취 수정 (관리자만)
   */
  @PutMapping("/{id}")
  public ResponseEntity<TrecordDto> update(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id,
      @RequestBody TrecordDto dto
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireAdmin(ai);
    return ResponseEntity.ok(recordSvc.update(id, dto));
  }

  /**
   * 6) 녹취 삭제 (관리자만)
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireAdmin(ai);
    recordSvc.delete(id);
    return ResponseEntity.noContent().build();
  }

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  static class UnauthorizedException extends RuntimeException {
    UnauthorizedException(String msg){ super(msg); }
  }
  @ResponseStatus(HttpStatus.FORBIDDEN)
  static class ForbiddenException extends RuntimeException {
    ForbiddenException(String msg){ super(msg); }
  }
}
