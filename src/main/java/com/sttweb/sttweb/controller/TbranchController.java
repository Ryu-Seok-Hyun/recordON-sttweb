// src/main/java/com/sttweb/sttweb/controller/TbranchController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class TbranchController {

  private final TbranchService svc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;

  private ResponseEntity<String> checkAdmin(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("토큰이 없습니다.");
    }
    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
    }
    String userId = jwtTokenProvider.getUserId(token);
    Info me = memberSvc.getMyInfoByUserId(userId);
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
    }
    return null;
  }

  /** 지점 전체 조회 (관리자만) */
  @LogActivity(
      type     = "branch",
      activity = "조회",
      contents = "지점 전체 조회"
  )
  @GetMapping
  public ResponseEntity<?> listAll(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      Pageable pageable
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;
    Page<TbranchDto> page = svc.findAll(pageable);
    return ResponseEntity.ok(page);
  }

  /** 지점 단건 조회 (관리자만) */
  @LogActivity(
      type     = "branch",
      activity = "조회",
      contents = "#지점 단건 조회"
  )
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

  /** 지점 등록 (관리자만) */
  @LogActivity(
      type     = "branch",
      activity = "등록",
      contents = "#지점 등록"
  )
  @PostMapping
  public ResponseEntity<?> createBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestBody TbranchDto reqDto
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;
    TbranchDto created = svc.createBranch(reqDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /** 지점 수정 (관리자만) */
  @LogActivity(
      type     = "branch",
      activity = "수정",
      contents = "'지점 수정"
  )
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
  @LogActivity(
      type     = "branch",
      activity = "비활성화",
      contents = "지점 비활성 함"
  )
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
  @LogActivity(
      type     = "branch",
      activity = "활성화",
      contents = "지점 활성화 함"
  )
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
