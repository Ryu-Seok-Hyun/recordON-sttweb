package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.exception.ForbiddenException;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TactivitylogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activitylogs")
@RequiredArgsConstructor
public class ActivityLogController {

  private final TactivitylogService logService;
  private final JwtTokenProvider    jwtTokenProvider;

  /* ───────────────────────── private helpers ───────────────────────── */

  /** “Bearer …” 접두어 제거 후 순수 JWT 만 반환 (없으면 null) */
  private String extractToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    return authHeader.substring(7).trim();
  }

  /* ──────────────────────────── endpoints ──────────────────────────── */

  /** 페이징 + 필터 조회 */
  @GetMapping
  public ResponseEntity<Page<TactivitylogDto>> listLogs(
      @RequestParam(defaultValue = "0")  int    page,
      @RequestParam(defaultValue = "10") int    size,
      @RequestParam(required = false)    String startCrtime,
      @RequestParam(required = false)    String endCrtime,
      @RequestParam(required = false)    String type,
      @RequestParam(required = false)    String searchField,
      @RequestParam(required = false)    String keyword,
      @RequestHeader(value = "Authorization", required = false) String authHeader
  ) {
    /* 1) 토큰 검증 */
    String token = extractToken(authHeader);
    if (token == null || !jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /* 2) 사용자 정보 */
    String  userId    = jwtTokenProvider.getUserId(token);
    String  userLevel = jwtTokenProvider.getUserLevel(token);   // ← 수정

    /* 3) 페이징 */
    Pageable pageable = PageRequest.of(page, size, Sort.by("crtime").descending());

    /* 4) 서비스 호출 */
    Page<TactivitylogDto> result = logService.getLogsWithFilter(
        userId, userLevel,
        startCrtime, endCrtime,
        type,
        searchField, keyword,
        pageable
    );
    return ResponseEntity.ok(result);
  }

  /** 단건 조회 */
  @GetMapping("/{id}")
  public ResponseEntity<TactivitylogDto> getLog(
      @PathVariable Integer id,
      @RequestHeader(value = "Authorization", required = false) String authHeader
  ) {
    String token = extractToken(authHeader);
    if (token == null || !jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String userId    = jwtTokenProvider.getUserId(token);
    String userLevel = jwtTokenProvider.getUserLevel(token);    // ← 수정

    TactivitylogDto dto = logService.getLog(id);
    if (!"0".equals(userLevel) && !userId.equals(dto.getUserId())) {
      throw new ForbiddenException("관리자 권한이 필요합니다.");
    }
    return ResponseEntity.ok(dto);
  }

  /** 삭제 */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteLog(
      @PathVariable Integer id,
      @RequestHeader(value = "Authorization", required = false) String authHeader
  ) {
    String token = extractToken(authHeader);
    if (token == null || !jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String userId    = jwtTokenProvider.getUserId(token);
    String userLevel = jwtTokenProvider.getUserLevel(token);    // ← 수정

    TactivitylogDto dto = logService.getLog(id);
    if (!"0".equals(userLevel) && !userId.equals(dto.getUserId())) {
      throw new ForbiddenException("관리자 권한이 필요합니다.");
    }

    logService.deleteLog(id);
    return ResponseEntity.noContent().build();
  }
}
