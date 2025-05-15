// src/main/java/com/sttweb/sttweb/controller/UserPermissionController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.UserPermissionViewDto;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.service.TmemberService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user-permissions")
@RequiredArgsConstructor
public class UserPermissionController {

  private final UserPermissionRepository repo;
  private final TmemberService memberSvc;

  @LogActivity(
      type     = "role",
      activity = "부여",
      contents = "권한부여"
  )
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> grant(@RequestBody GrantDto dto) {
    Integer granteeSeq = memberSvc
        .getMyInfoByUserId(dto.getGranteeUserId())
        .getMemberSeq();
    Integer targetSeq = memberSvc
        .getMyInfoByUserId(dto.getTargetUserId())
        .getMemberSeq();

    UserPermission up = repo
        .findByGranteeSeqAndTargetSeq(granteeSeq, targetSeq)
        .orElseGet(UserPermission::new);
    up.setGranteeSeq(granteeSeq);
    up.setTargetSeq(targetSeq);
    up.setPermLevel(dto.getPermLevel());
    repo.save(up);

    return ResponseEntity.ok().build();
  }

  @LogActivity(
      type     = "role",
      activity = "회수",
      contents = "권한회수"
  )
  @DeleteMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> revoke(@RequestBody GrantDto dto) {
    Integer granteeSeq = memberSvc
        .getMyInfoByUserId(dto.getGranteeUserId())
        .getMemberSeq();
    Integer targetSeq = memberSvc
        .getMyInfoByUserId(dto.getTargetUserId())
        .getMemberSeq();

    repo.deleteByGranteeSeqAndTargetSeq(granteeSeq, targetSeq);
    return ResponseEntity.noContent().build();
  }

  @LogActivity(
      type     = "role",
      activity = "조회",
      contents = "권한조회"
  )
  @GetMapping("/{granteeUserId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserPermissionViewDto>> viewPermissions(
      @PathVariable("granteeUserId") String granteeUserId
  ) {
    Integer granteeSeq = memberSvc
        .getMyInfoByUserId(granteeUserId)
        .getMemberSeq();

    List<UserPermissionViewDto> list = memberSvc.getAllMembers().stream()
        .map(m -> {
          int level = repo.findByGranteeSeqAndTargetSeq(granteeSeq, m.getMemberSeq())
              .map(UserPermission::getPermLevel)
              .orElse(1);
          return new UserPermissionViewDto(
              m.getUserId(),
              level >= 2,
              level >= 3,
              level >= 4
          );
        })
        .collect(Collectors.toList());

    return ResponseEntity.ok(list);
  }
}
