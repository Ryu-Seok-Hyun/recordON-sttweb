// src/main/java/com/sttweb/sttweb/controller/MemberLinePermController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.GrantLineDto;
import com.sttweb.sttweb.dto.MemberLinePermDto;
import com.sttweb.sttweb.service.MemberLinePermService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/perm")
@RequiredArgsConstructor
public class MemberLinePermController {

  private final MemberLinePermService service;


  /**
   * 0) 전체 매핑 조회
   *   → tmember_line_perm 테이블의 모든 행을 DTO 리스트로 반환
   */
  @GetMapping
  public ResponseEntity<List<MemberLinePermDto>> getAllMappings() {
    List<MemberLinePermDto> list = service.getAllMappings();
    return ResponseEntity.ok(list);
  }

  /**
   * GET /api/perm/member/{memberSeq}
   *   → 해당 회원(memberSeq)이 갖고 있는 회선별 권한 목록 반환
   */
  @GetMapping("/member/{memberSeq}")
  public ResponseEntity<List<MemberLinePermDto>> getByMember(
      @PathVariable("memberSeq") Integer memberSeq
  ) {
    List<MemberLinePermDto> list = service.getPermissionsByMember(memberSeq);
    return ResponseEntity.ok(list);
  }

  /**
   * GET /api/perm/line/{lineId}
   *   → 해당 회선(lineId)에 매핑된 회원별 권한 목록 반환
   */
  @GetMapping("/line/{lineId}")
  public ResponseEntity<List<MemberLinePermDto>> getByLine(
      @PathVariable("lineId") Integer lineId
  ) {
    List<MemberLinePermDto> list = service.getPermissionsByLine(lineId);
    return ResponseEntity.ok(list);
  }

  /**
   * POST /api/perm/grant-line
   *   → requestBody 로 들어온 (memberSeq, lineId, roleSeq) 조합을
   *      tmember_line_perm 테이블에 INSERT (중복은 스킵)
   *
   * 예) Request JSON:
   * {
   *   "memberSeq": 3,
   *   "lineId":    5,
   *   "roleSeq":   3
   * }
   */
  @PostMapping("/grant-line")
  public ResponseEntity<String> grantLinePermission(@RequestBody GrantLineDto dto) {
    boolean granted = service.grantLinePermission(
        dto.getMemberSeq(), dto.getLineId(), dto.getRoleSeq()
    );
    if (granted) {
      return ResponseEntity.ok("회선 권한이 부여되었습니다.");
    } else {
      return ResponseEntity.badRequest()
          .body("이미 권한이 부여되었거나, 잘못된 요청입니다.");
    }
  }

  /**
   * DELETE /api/perm/revoke-line
   *   → requestBody 로 들어온 (memberSeq, lineId) 조합에 해당하는
   *      tmember_line_perm 행을 삭제
   *
   * 예) Request JSON:
   * {
   *   "memberSeq": 3,
   *   "lineId":    5
   * }
   */
  @DeleteMapping("/revoke-line")
  public ResponseEntity<String> revokeLinePermission(@RequestBody GrantLineDto dto) {
    boolean revoked = service.revokeLinePermission(
        dto.getMemberSeq(), dto.getLineId()
    );
    if (revoked) {
      return ResponseEntity.ok("회선 권한이 회수되었습니다.");
    } else {
      return ResponseEntity.badRequest()
          .body("해당 권한 매핑이 존재하지 않습니다.");
    }
  }
}
