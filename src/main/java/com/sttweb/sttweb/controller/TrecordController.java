// src/main/java/com/sttweb/sttweb/controller/TrecordController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private final TrecordService recordSvc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;

  /**
   * 단순 인증 검사: 토큰 유무/유효성만 확인
   * @return null 이면 통과, 아니면 즉시 반환할 ResponseEntity<String>
   */
  private ResponseEntity<String> checkAuth(String authHeader) {
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
    return null;
  }

  /**
   * 관리자 권한 검사: 인증 후 userLevel == "0" 체크
   */
  private ResponseEntity<String> checkAdmin(String authHeader) {
    ResponseEntity<String> err = checkAuth(authHeader);
    if (err != null) return err;
    String token = authHeader.substring(7);
    String userId = jwtTokenProvider.getUserId(token);
    Info me = memberSvc.getMyInfoByUserId(userId);
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity
          .status(HttpStatus.FORBIDDEN)
          .body("권한이 없습니다.");
    }
    return null;
  }

  /** 전체 녹취 조회 (모두 읽기 가능) */
  @GetMapping
  public ResponseEntity<?> listAll(
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    ResponseEntity<String> err = checkAuth(authHeader);
    if (err != null) return err;
    List<TrecordDto> all = recordSvc.findAll();
    return ResponseEntity.ok(all);
  }

  /** 번호로 검색 (모두 읽기 가능) */
  @GetMapping("/search")
  public ResponseEntity<?> searchByNumber(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestParam(required=false) String number1,
      @RequestParam(required=false) String number2
  ) {
    ResponseEntity<String> err = checkAuth(authHeader);
    if (err != null) return err;
    List<TrecordDto> results = recordSvc.searchByNumber(number1, number2);
    return ResponseEntity.ok(results);
  }

  /** 단건 조회 (모두 읽기 가능) */
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    ResponseEntity<String> err = checkAuth(authHeader);
    if (err != null) return err;
    TrecordDto dto = recordSvc.findById(id);
    return ResponseEntity.ok(dto);
  }

  /** 녹취 등록 (관리자만) */
  @PostMapping
  public ResponseEntity<?> create(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestBody TrecordDto dto
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;
    TrecordDto created = recordSvc.create(dto);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(created);
  }

  /** 녹취 수정 (관리자만) */
  @PutMapping("/{id}")
  public ResponseEntity<?> update(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id,
      @RequestBody TrecordDto dto
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;
    TrecordDto updated = recordSvc.update(id, dto);
    return ResponseEntity.ok(updated);
  }

  /** 녹취 삭제 (관리자만) */
  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;
    recordSvc.delete(id);
    return ResponseEntity.noContent().build();
  }
}
