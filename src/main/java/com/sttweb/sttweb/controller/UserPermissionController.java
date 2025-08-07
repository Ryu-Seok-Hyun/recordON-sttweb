// UserPermissionController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.service.PermissionService;
import com.sttweb.sttweb.service.TmemberService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user-permissions")
@RequiredArgsConstructor
public class UserPermissionController {

  private final PermissionService permissionService;
  private final UserPermissionRepository repo;
  private final TmemberService      memberSvc;

  @LogActivity(
      type     = "permission",
      activity = "권한부여",
      contents = "#{ "
          +  "'사용자 ' + #userId "
          +  " + '이(가) 사용자 ' "
          +  " + #tmemberService.getMyInfoByMemberSeq(#grants[0].memberSeq).getUserId() "
          +  " + '에게 권한 부여' "
          +  "}"
  )
  @PostMapping
  public ResponseEntity<Void> grant(@RequestBody List<GrantDto> grants) {
    grants.forEach(permissionService::grantAndSyncLinePerm);
    return ResponseEntity.ok().build();
  }

  @LogActivity(
      type     = "permission",
      activity = "권한회수",
      contents = "#{ '사용자 ' + #userId + '이(가) 사용자 ' + #dtos[0].granteeUsername + '의 권한을 회수' }"
  )
  @DeleteMapping
  public ResponseEntity<Void> revokeAll(@RequestBody List<GrantDto> dtos) {
    dtos.forEach(d -> permissionService.revokeAndSyncLinePerm(d.getMemberSeq(), d.getLineId()));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{memberSeq}")
  public ResponseEntity<List<GrantDto>> listPermissions(@PathVariable Integer memberSeq) {
    var result = repo.findByMemberSeq(memberSeq).stream()
        .map(up -> {
          // MemberSeq로 사용자 ID(또는 이름)를 조회
          String username = memberSvc.getMyInfoByMemberSeq(up.getMemberSeq())
              .getUserId();
          return GrantDto.builder()
              .memberSeq(up.getMemberSeq())
              .lineId(up.getLineId())
              .permLevel(up.getPermLevel())
              .granteeUsername(username)
              .build();
        })
        .collect(Collectors.toList());
    return ResponseEntity.ok(result);
  }
}
