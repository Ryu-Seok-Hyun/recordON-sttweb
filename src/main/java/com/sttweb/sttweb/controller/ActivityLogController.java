package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.exception.ForbiddenException;
import com.sttweb.sttweb.service.TactivitylogService;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activitylogs")
@RequiredArgsConstructor
public class ActivityLogController {

  private final TactivitylogService logService;
  private final JwtTokenProvider jwtTokenProvider;

  /**
   * Authorization 헤더에서 "Bearer " 제거
   * @return 순수 JWT 문자열 (없거나 형식이 맞지 않으면 null)
   */
  private String extractToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    return authHeader.substring(7).trim();
  }

  /** 페이징 + 필터 조회 */
  @GetMapping
  public ResponseEntity<Page<TactivitylogDto>> listLogs(
      @RequestParam(name="page",       defaultValue="0")      int page,
      @RequestParam(name="size",       defaultValue="10")     int size,
      @RequestParam(name="startCrtime", required=false)       String startCrtime,
      @RequestParam(name="endCrtime",   required=false)       String endCrtime,
      @RequestParam(name="type",        required=false)       String type,
      @RequestParam(name="searchField", required=false)       String searchField,
      @RequestParam(name="keyword",     required=false)       String keyword,
      @RequestHeader(value="Authorization", required=false)   String authHeader
  ) {
    // 1) 토큰 추출 및 검증
    String token = extractToken(authHeader);
    if (token == null || !jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // 2) 사용자 정보 추출
    String userId    = jwtTokenProvider.getUserId(token);
    // 기존 getUserLevel → getRoles 로 변경
    String userLevel = jwtTokenProvider.getRoles(token);

    // 3) 페이징 및 정렬 설정
    Pageable pageable = PageRequest.of(page, size, Sort.by("crtime").descending());

    // 4) 서비스 호출
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
      @PathVariable("id") Integer id,
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    String token = extractToken(authHeader);
    if (token == null || !jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String userId    = jwtTokenProvider.getUserId(token);
    String userLevel = jwtTokenProvider.getRoles(token);

    TactivitylogDto dto = logService.getLog(id);
    if (!"0".equals(userLevel) && !userId.equals(dto.getUserId())) {
      throw new ForbiddenException("관리자 권한이 필요합니다.");
    }
    return ResponseEntity.ok(dto);
  }

  /** 삭제 */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteLog(
      @PathVariable("id") Integer id,
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    String token = extractToken(authHeader);
    if (token == null || !jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String userId    = jwtTokenProvider.getUserId(token);
    String userLevel = jwtTokenProvider.getRoles(token);

    TactivitylogDto dto = logService.getLog(id);
    if (!"0".equals(userLevel) && !userId.equals(dto.getUserId())) {
      throw new ForbiddenException("관리자 권한이 필요합니다.");
    }

    logService.deleteLog(id);
    return ResponseEntity.noContent().build();
  }
}
