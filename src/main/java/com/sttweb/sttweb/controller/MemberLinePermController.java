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

  /** 0) 전체 매핑 조회 */
  @GetMapping
  public ResponseEntity<List<MemberLinePermDto>> getAllMappings() {
    return ResponseEntity.ok(service.getAllMappings());
  }

  /** 1) 회원별 실제로 부여된 매핑 조회 */
  @GetMapping("/member/{memberSeq}")
  public ResponseEntity<List<MemberLinePermDto>> getByMember(
      @PathVariable("memberSeq") Integer memberSeq
  ) {
    return ResponseEntity.ok(service.getPermissionsByMember(memberSeq));
  }

  /** 1-1) 회원별 전체 회선 + 권한 유무 조회 */
  @GetMapping("/member/{memberSeq}/all-lines")
  public ResponseEntity<List<MemberLinePermDto>> getAllLinesWithPerm(
      @PathVariable("memberSeq") Integer memberSeq
  ) {
    return ResponseEntity.ok(service.getAllLinesWithPerm(memberSeq));
  }

  /** 1-2) 모든 회원 × 전체 회선 조합 조회 */
  @GetMapping("/all-members-lines")
  public ResponseEntity<List<MemberLinePermDto>> getAllMembersAllLinesPerm() {
    return ResponseEntity.ok(service.getAllMembersAllLinesPerm());
  }

  /** 2) 회선별 매핑 조회 */
  @GetMapping("/line/{lineId}")
  public ResponseEntity<List<MemberLinePermDto>> getByLine(
      @PathVariable("lineId") Integer lineId
  ) {
    return ResponseEntity.ok(service.getPermissionsByLine(lineId));
  }

  /** 3) 회선 권한 부여 */
  @PostMapping("/grant-line")
  public ResponseEntity<String> grantLinePermission(@RequestBody GrantLineDto dto) {
    boolean granted = service.grantLinePermission(
        dto.getMemberSeq(), dto.getLineId(), dto.getRoleSeq()
    );
    if (granted) {
      return ResponseEntity.ok("회선 권한이 부여되었습니다.");
    } else {
      return ResponseEntity.badRequest()
          .body("잘못된 요청입니다.");
    }
  }

  /** 4) 회선 권한 회수 */
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
