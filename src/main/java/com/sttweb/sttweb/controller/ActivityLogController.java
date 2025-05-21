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

  private String extractToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    return authHeader.substring(7);
  }

  /** 페이징 + 필터 조회 */
  @GetMapping
  public ResponseEntity<Page<TactivitylogDto>> listLogs(
      @RequestParam(name="page",       defaultValue="0")           int page,
      @RequestParam(name="size",       defaultValue="10")          int size,
      @RequestParam(name="startCrtime", required=false)          String startCrtime,
      @RequestParam(name="endCrtime",   required=false)          String endCrtime,
      @RequestParam(name="type",        required=false)          String type,
      @RequestParam(name="searchField", required=false)          String searchField,
      @RequestParam(name="keyword",     required=false)          String keyword,
      @RequestHeader(value="Authorization", required=false)      String authHeader
  ) {
    // JWT 검증
    String token = extractToken(authHeader);
    if (token == null || !jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String userId    = jwtTokenProvider.getUserId(token);
    String userLevel = jwtTokenProvider.getUserLevel(token);

    Pageable pageable = PageRequest.of(page, size, Sort.by("crtime").descending());

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
    String userLevel = jwtTokenProvider.getUserLevel(token);

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
    String userLevel = jwtTokenProvider.getUserLevel(token);

    TactivitylogDto dto = logService.getLog(id);
    if (!"0".equals(userLevel) && !userId.equals(dto.getUserId())) {
      throw new ForbiddenException("관리자 권한이 필요합니다.");
    }
    logService.deleteLog(id);
    return ResponseEntity.noContent().build();
  }
}
