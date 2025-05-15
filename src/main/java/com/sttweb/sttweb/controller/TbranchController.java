package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 지점 파트
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class TbranchController {

  private final TbranchService svc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;

  /**
   * 토큰 검사 + ADMIN 여부 체크 (userLevel == "0")
   */
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
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자만 접근 가능합니다.");
    }
    return null;
  }

  /**
   * 본사 관리자(HQ Admin) 체크 (hqYn == "0" 이면 본사)
   */
  private ResponseEntity<String> validateHqAdmin(String authHeader) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;
    String token = authHeader.substring(7);
    String userId = jwtTokenProvider.getUserId(token);
    Info me = memberSvc.getMyInfoByUserId(userId);
    Integer myBranchSeq = me.getBranchSeq();
    if (myBranchSeq == null) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("본사 지점 정보가 설정되어 있지 않습니다.");
    }
    TbranchDto myBranch = svc.findById(myBranchSeq);
    if (!"0".equals(myBranch.getHqYn())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("본사 관리자만 수행 가능합니다.");
    }
    return null;
  }

  /**
   * HQ Admin 또는 자기 지점인 Branch Admin 체크
   * (hqYn == "0" 인 HQ Admin은 모든 지점 허용,
   *  그렇지 않은 Branch Admin은 자기 지점만 허용)
   */
  private ResponseEntity<String> validateBranchAdminOrHq(String authHeader, Integer branchId) {
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;
    String token = authHeader.substring(7);
    String userId = jwtTokenProvider.getUserId(token);
    Info me = memberSvc.getMyInfoByUserId(userId);
    Integer myBranchSeq = me.getBranchSeq();
    if (myBranchSeq == null) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("지점 정보가 설정되어 있지 않습니다.");
    }
    TbranchDto myBranch = svc.findById(myBranchSeq);
    // HQ Admin(hqYn == "0")은 모두 허용
    if ("0".equals(myBranch.getHqYn())) {
      return null;
    }
    // Branch Admin은 자신의 지점만 허용
    if (myBranchSeq.equals(branchId)) {
      return null;
    }
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("지사 관리자 권한이 없습니다.");
  }

  /**
   * 지점 조회 (본사: 전체 조회, 지사: 본인 지점만)
   */
  @LogActivity(type = "branch", activity = "조회", contents = "지점 조회")
  @GetMapping
  public ResponseEntity<?> listAll(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      Pageable pageable
  ) {
    // 관리자 여부 확인
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;
    String token = authHeader.substring(7);
    String userId = jwtTokenProvider.getUserId(token);
    Info me = memberSvc.getMyInfoByUserId(userId);
    Integer myBranchSeq = me.getBranchSeq();
    if (myBranchSeq == null) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("지점 정보가 설정되어 있지 않습니다.");
    }
    TbranchDto myBranch = svc.findById(myBranchSeq);
    // 본사 관리자 : 전체 조회
    if ("0".equals(myBranch.getHqYn())) {
      Page<TbranchDto> page = svc.findAll(pageable);
      return ResponseEntity.ok(page);
    }
    // 지사 관리자 : 본인 지점만
    TbranchDto dto = svc.findById(myBranchSeq);
    Page<TbranchDto> page = new org.springframework.data.domain.PageImpl<>(
        java.util.List.of(dto), pageable, 1
    );
    return ResponseEntity.ok(page);
  }

  /** 지점 단건 조회 (본사: 모든 지점, 지사: 본인 지점만) */
  @LogActivity(type = "branch", activity = "조회", contents = "지점 단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    // 관리자 여부 확인
    ResponseEntity<String> err = checkAdmin(authHeader);
    if (err != null) return err;

    // 토큰에서 userId 추출 및 사용자 정보 조회
    String token = authHeader.substring(7);
    String userId = jwtTokenProvider.getUserId(token);
    Info me = memberSvc.getMyInfoByUserId(userId);
    Integer myBranchSeq = me.getBranchSeq();
    if (myBranchSeq == null) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("지점 정보가 설정되어 있지 않습니다.");
    }

    // 소속 지점 정보
    TbranchDto myBranch = svc.findById(myBranchSeq);
    // 본사 관리자(hqYn == "0")이거나, 자신의 지점 조회
    if ("0".equals(myBranch.getHqYn()) || myBranchSeq.equals(id)) {
      TbranchDto dto = svc.findById(id);
      return ResponseEntity.ok(dto);
    }

    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body("지사 관리자 권한이 없습니다.");
  }
  /**
   * 지점 등록 (본사 관리자만)
   */
  @LogActivity(type = "branch", activity = "등록", contents = "지점 등록")
  @PostMapping
  public ResponseEntity<?> createBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestBody TbranchDto reqDto
  ) {
    ResponseEntity<String> err = validateHqAdmin(authHeader);
    if (err != null) return err;
    TbranchDto created = svc.createBranch(reqDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * 지점 수정 (본사 또는 지사 자신의 지점)
   */
  @LogActivity(type = "branch", activity = "수정", contents = "지점 수정")
  @PutMapping("/{id}")
  public ResponseEntity<?> update(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id,
      @RequestBody TbranchDto dto
  ) {
    ResponseEntity<String> err = validateBranchAdminOrHq(authHeader, id);
    if (err != null) return err;
    TbranchDto updated = svc.update(id, dto);
    return ResponseEntity.ok(updated);
  }

  /**
   * 지점 비활성화 (본사 관리자만)
   */
  @LogActivity(type = "branch", activity = "비활성화", contents = "지점 비활성 함")
  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    ResponseEntity<String> err = validateHqAdmin(authHeader);
    if (err != null) return err;
    svc.changeStatus(id, false);
    return ResponseEntity.noContent().build();
  }

  /**
   * 지점 활성화 (본사 관리자만)
   */
  @LogActivity(type = "branch", activity = "활성화", contents = "지점 활성화 함")
  @PutMapping("/{id}/activate")
  public ResponseEntity<?> activateBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    ResponseEntity<String> err = validateHqAdmin(authHeader);
    if (err != null) return err;
    svc.changeStatus(id, true);
    return ResponseEntity.ok().build();
  }
}
