package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class TbranchController {

  private final TbranchService svc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;

  /**
   * 1) 헤더에 토큰이 있는지
   * 2) 토큰이 유효한지
   * 3) userLevel == "0"(관리자) 인지
   * 를 모두 검사하는 헬퍼
   */
  private ResponseEntity<String> checkAdmin(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return ResponseEntity
          .status(HttpStatus.UNAUTHORIZED)
          .body("토큰이 없습니다.");
    }
    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      return ResponseEntity
          .status(HttpStatus.UNAUTHORIZED)
          .body("유효하지 않은 토큰입니다.");
    }
    String userId = jwtTokenProvider.getUserId(token);
    Info me = memberSvc.getMyInfoByUserId(userId);
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity
          .status(HttpStatus.FORBIDDEN)
          .body("권한이 없습니다.");
    }
    return null;
  }

  /** 지점 전체 조회 (관리자만) */
  @GetMapping
  public ResponseEntity<?> listAll(
      @RequestHeader(value = "Authorization", required = false) String authHeader
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;

    List<TbranchDto> all = svc.findAll();
    return ResponseEntity.ok(all);
  }

  /** 지점 단건 조회 (관리자만) */
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;

    TbranchDto dto = svc.findById(id);
    return ResponseEntity.ok(dto);
  }

  /** 지점 수정 (관리자만) */
  @PutMapping("/{id}")
  public ResponseEntity<?> update(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id,
      @RequestBody TbranchDto dto
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;

    TbranchDto updated = svc.update(id, dto);
    return ResponseEntity.ok(updated);
  }

  /** 지점 비활성화 (관리자만) */
  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;

    svc.changeStatus(id, false);
    return ResponseEntity.noContent().build();
  }

  /** 지점 활성화 (관리자만) */
  @PutMapping("/{id}/activate")
  public ResponseEntity<?> activateBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;

    svc.changeStatus(id, true);
    return ResponseEntity.ok().build();
  }
}
