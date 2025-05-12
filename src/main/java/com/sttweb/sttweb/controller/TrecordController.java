package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.ListResponse;
import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import com.sttweb.sttweb.service.TmemberRoleService;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private final TrecordService recordSvc;
  private final TmemberService memberSvc;
  private final TmemberRoleService roleSvc;
  private final JwtTokenProvider jwtTokenProvider;

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

  private void requireMinimumRole(AuthInfo ai, int minRole) {
    if (ai.roleSeq < minRole) {
      throw new ForbiddenException("권한이 없습니다.");
    }
  }

  private void requireAdmin(AuthInfo ai) {
    if (ai.roleSeq < 4) {
      throw new ForbiddenException("관리자 권한이 필요합니다.");
    }
  }

  /** 1) 전체 녹취 조회 (READ 이상) */
  @GetMapping
  public ResponseEntity<ListResponse<TrecordDto>> listAll(
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireMinimumRole(ai, 2);

    List<TrecordDto> sorted = recordSvc.findAll().stream()
        .filter(r -> ai.roleSeq > 3 || ai.myNumber.equals(r.getNumber1()))
        .sorted(Comparator.comparing(TrecordDto::getRecordSeq))
        .collect(Collectors.toList());

    return ResponseEntity.ok(new ListResponse<>(sorted.size(), sorted));
  }

  /** 2) 번호로 검색 (READ 이상) */
  @GetMapping("/search")
  public ResponseEntity<?> searchByNumber(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestParam(value="number1", required=false) String number1,
      @RequestParam(value="number2", required=false) String number2
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireMinimumRole(ai, 2);

    List<TrecordDto> filtered = recordSvc.searchByNumber(number1, number2).stream()
        .filter(r -> ai.roleSeq > 3 || ai.myNumber.equals(r.getNumber1()))
        .sorted(Comparator.comparing(TrecordDto::getRecordSeq))
        .collect(Collectors.toList());

    if (filtered.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.TEXT_PLAIN)
          .body("조건에 부합하는 데이터가 없습니다.");
    }
    return ResponseEntity.ok(new ListResponse<>(filtered.size(), filtered));
  }

  /** 3) 단건 조회 (READ 이상) */
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
      throw new ForbiddenException("본인자료 외에 검색할 수 없습니다.");
    }
    return ResponseEntity.ok(dto);
  }

  /** 4) 녹취 등록 (관리자만) */
  @PostMapping
  public ResponseEntity<TrecordDto> create(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestBody TrecordDto dto
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireAdmin(ai);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(recordSvc.create(dto));
  }

  /** 5) 녹취 수정 (관리자만) */
  @PutMapping("/{id}")
  public ResponseEntity<TrecordDto> update(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id,
      @RequestBody TrecordDto dto
  ) {
    AuthInfo ai = authenticate(authHeader);
    requireAdmin(ai);
    TrecordDto updated = recordSvc.update(id, dto);
    return ResponseEntity.ok(updated);
  }

  /** 6) 녹취 삭제 (관리자만) */
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
