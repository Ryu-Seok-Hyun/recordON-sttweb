package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.BranchSwitchService;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

// SwitchLoginController 세트

@RestController
@RequestMapping("/api/branch")
@RequiredArgsConstructor
public class BranchSwitchController {

  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;
  private final BranchSwitchService switchService;
  private final TbranchService branchSvc;

  @GetMapping("/switch/{branchSeq}")
  public ResponseEntity<Void> switchToBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("branchSeq") Integer branchSeq
  ) {
    if (authHeader == null || !authHeader.startsWith("Bearer "))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

    String originalToken = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(originalToken))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

    String userId = jwtTokenProvider.getUserId(originalToken);
    Info currentUser = memberSvc.getMyInfoByUserId(userId);

    if (!"0".equals(currentUser.getUserLevel()) && !"3".equals(currentUser.getUserLevel()))
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

    TbranchDto target = branchSvc.findById(branchSeq);
    if (target == null)
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

    // 직접 pIp/pPort 또는 pbIp/pbPort 필드 사용
    boolean external = Integer.valueOf(1).equals(target.getIpType());
    String branchIp   = external ? target.getPbIp()   : target.getPIp();
    String branchPort = external ? target.getPbPort() : target.getPPort();

    if (branchIp == null || branchPort == null)
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

    String switchToken = switchService.createSwitchToken(originalToken, userId, branchSeq);

    String targetUrl = String.format(
        "http://%s:%s/auth/switchLogin?switch_token=%s",
        branchIp,
        branchPort,
        UriUtils.encode(switchToken, StandardCharsets.UTF_8)
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create(targetUrl));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }
}