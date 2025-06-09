package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.TbranchRepository;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class TbranchController {

  private final TbranchService branchSvc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;
  private final TbranchRepository branchRepository;

  /**
   * 1) 공통: Authorization 헤더에 Bearer 토큰 유효성 검사
   */
  private ResponseEntity<String> checkToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body("토큰이 없습니다.");
    }
    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body("유효하지 않은 토큰입니다.");
    }
    return null;
  }

  /**
   * 토큰에서 userId 꺼내오기
   */
  private Info getMe(String authHeader) {
    String token = authHeader.substring(7);
    String userId = jwtTokenProvider.getUserId(token);
    return memberSvc.getMyInfoByUserId(userId);
  }

  /**
   * 지점 목록 조회
   * - 본사 관리자(0): 전체 or 키워드 검색
   * - 지사 관리자(1): 자기 지점만 (키워드는 무시)
   * - 그 외(2): 403
   */
  @LogActivity(type = "branch", activity = "조회", contents = "지점 조회")
  @GetMapping
  public ResponseEntity<?> listAll(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "keyword", required = false) String keyword,
      Pageable pageable
  ) {
    // 1) 토큰 검사
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;

    // 2) 내 정보(userLevel, branchSeq)
    Info me = getMe(authHeader);
    String lvl = me.getUserLevel();
    Integer myBranchSeq = me.getBranchSeq();

    if ("0".equals(lvl)) {
      // 본사 관리자 → 전체 or 검색
      if (keyword != null && !keyword.isBlank()) {
        Page<TbranchDto> searched = branchSvc.search(keyword.trim(), pageable);
        return ResponseEntity.ok(searched);
      } else {
        Page<TbranchDto> all = branchSvc.findAll(pageable);
        return ResponseEntity.ok(all);
      }

    } else if ("1".equals(lvl)) {
      // 지사 관리자 → 자신의 지점만
      if (myBranchSeq == null) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("내 지점 정보가 설정되어 있지 않습니다.");
      }
      TbranchDto dto = branchSvc.findById(myBranchSeq);
      Page<TbranchDto> single =
          new PageImpl<>(List.of(dto), pageable, 1);
      return ResponseEntity.ok(single);

    } else {
      // 일반 유저
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("권한이 없습니다.");
    }
  }

  /**
   * 지점 단건 조회
   * - 본사 관리자(0): 모든 지점
   * - 지사 관리자(1): 자신의 지점만
   * - 그 외: 403
   */
  @LogActivity(type = "branch", activity = "조회", contents = "지점 단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    // 1) 토큰 검사
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;

    // 2) 내 정보
    Info me = getMe(authHeader);
    String lvl = me.getUserLevel();
    Integer myBranchSeq = me.getBranchSeq();

    // 3) 분기
    if ("0".equals(lvl)) {
      // 본사 관리자
      return ResponseEntity.ok(branchSvc.findById(id));

    } else if ("1".equals(lvl) && myBranchSeq != null && myBranchSeq.equals(id)) {
      // 지사 관리자(자기관할 지점만)
      return ResponseEntity.ok(branchSvc.findById(id));

    } else {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("권한이 없습니다.");
    }
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
    // 1) 토큰 검사
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;
    // 2) 본사 관리자 여부
    Info me = getMe(authHeader);
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("본사 관리자만 접근 가능합니다.");
    }
    // 3) 생성
    TbranchDto created = branchSvc.createBranch(reqDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * 지점 수정 (본사 관리자 OR 지사 관리자(자기관할 지점만))
   */
  @LogActivity(type = "branch", activity = "수정", contents = "지점 수정")
  @PutMapping("/{id}")
  public ResponseEntity<?> updateBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id,
      @RequestBody TbranchDto dto
  ) {
    // 1) 토큰 검사
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;

    // 2) 권한 검사
    Info me = getMe(authHeader);
    String lvl = me.getUserLevel();
    Integer myBranchSeq = me.getBranchSeq();

    if ("0".equals(lvl) || ("1".equals(lvl) && myBranchSeq != null && myBranchSeq.equals(id))) {
      // 본사 관리자 OR 지사 관리자(자기관할 지점)
      TbranchDto updated = branchSvc.update(id, dto);
      return ResponseEntity.ok(updated);
    } else {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("권한이 없습니다.");
    }
  }

  /**
   * 지점 비활성화 (본사 관리자만)
   */
  @LogActivity(type = "branch", activity = "비활성화", contents = "지점 비활성 함")
  @DeleteMapping("/{id}")
  public ResponseEntity<?> deactivateBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    // 1) 토큰 검사
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;

    // 2) 본사 관리자만
    Info me = getMe(authHeader);
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("본사 관리자만 접근 가능합니다.");
    }

    // 3) 비활성화
    branchSvc.changeStatus(id, false);
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
    // 1) 토큰 검사
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;

    // 2) 본사 관리자만
    Info me = getMe(authHeader);
    if (!"0".equals(me.getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("본사 관리자만 접근 가능합니다.");
    }

    // 3) 활성화
    branchSvc.changeStatus(id, true);
    return ResponseEntity.ok().build();
  }

  // ────────────────────────────────────────────────────────────────────────
  // 아래는 DB에 저장된 상태 컬럼을 조회해서 반환하는 상태 체크 API
  // ────────────────────────────────────────────────────────────────────────

  /**
   * 지점 상태 조회 (DB 저장된 is_alive, last_health_check, last_downtime 반환)
   * 호출 URL: GET /api/branches/{id}/health
   */
  @LogActivity(type = "branch", activity = "조회", contents = "지점 Health 조회 (DB 저장값)")
  @GetMapping("/{id}/health")
  public ResponseEntity<?> getBranchHealthFromDb(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    // 1) 토큰 검사
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;

    // 2) 해당 지점 정보 조회 (엔티티)
    TbranchEntity branchEntity = branchRepository.findById(id)
        .orElse(null);
    if (branchEntity == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body("존재하지 않는 지점입니다. id=" + id);
    }

    // 3) 권한 검사: 본사(0) or 지사 관리자(1)인 경우만 허용 (자기관할 지점만)
    Info me = getMe(authHeader);
    String lvl = me.getUserLevel();
    Integer myBranchSeq = me.getBranchSeq();
    if (!"0".equals(lvl)) {
      if (!("1".equals(lvl) && myBranchSeq != null && myBranchSeq.equals(id))) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("권한이 없습니다.");
      }
    }

    // 4) 결과 매핑
    Map<String, Object> result = new HashMap<>();
    result.put("branchSeq",       branchEntity.getBranchSeq());
    result.put("isAlive",         branchEntity.getIsAlive());
    result.put("lastHealthCheck", branchEntity.getLastHealthCheck());
    result.put("lastDowntime",    branchEntity.getLastDowntime());

    return ResponseEntity.ok(result);
  }

}
