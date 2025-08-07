// src/main/java/com/sttweb/sttweb/controller/BranchSwitchController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TmemberService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/branch")
@RequiredArgsConstructor
public class BranchSwitchController {
  private final JwtTokenProvider jwt;
  private final TmemberService memberSvc;

  @GetMapping("/switch/{branchSeq}")
  public ResponseEntity<Map<String,String>> switchToBranch(
      @RequestHeader("Authorization") String auth,
      @PathVariable("branchSeq") Integer branchSeq
  ) {
    String token = auth.substring(7).trim();
    if (!jwt.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    Info user = memberSvc.getMyInfoByUserId(jwt.getUserId(token));
    user.setCurrentBranchSeq(branchSeq);
    user.setCurrentBranchName(memberSvc.getBranchNameBySeq(branchSeq));
    String newToken = jwt.createTokenFromInfo(user);
    return ResponseEntity.ok(Map.of("token", newToken));
  }
}
