package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.BranchSwitchService;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

// BranchSwitchController 세트.

@RestController
@RequiredArgsConstructor
public class SwitchLoginController {

  private final BranchSwitchService switchService;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;
  private final TbranchService branchSvc;
  private final HttpServletRequest request;

  @GetMapping("/auth/switchLogin")
  public ResponseEntity<Void> consumeSwitchToken(
      @RequestParam("switch_token") String switchToken
  ) {
    String host = request.getServerName();
    int port    = request.getServerPort();
    String baseUrl = String.format("http://%s:%d", host, port);

    BranchSwitchService.BranchSwitchPayload payload;
    try {
      payload = switchService.validateAndConsume(switchToken);
    } catch (ExpiredJwtException e) {
      return redirectWithError(baseUrl, "expired"); // 만료됨
    } catch (JwtException | IllegalArgumentException e) {
      return redirectWithError(baseUrl, "invalid"); // 유효하지 않음
    }

    // 지점 검증
    List<TbranchEntity> cands = branchSvc.findByIpAndPort(host, String.valueOf(port));
    TbranchEntity selfBranch = cands.isEmpty() ? null : cands.get(0);
    if (selfBranch == null || !selfBranch.getBranchSeq().equals(payload.targetBranchSeq())) {
      return redirectWithError(baseUrl, "forbidden"); // 대상불일치
    }

    // 정상 처리: 기존 로그인 로직
    Info userInfo = memberSvc.getMyInfoByUserId(payload.userId());
    userInfo.setCurrentBranchSeq(selfBranch.getBranchSeq());
    userInfo.setCurrentBranchName(selfBranch.getCompanyName());
    String finalJwt = jwtTokenProvider.createTokenFromInfo(userInfo);

    // 성공 리다이렉트
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create(baseUrl + "/?token=" + UriUtils.encode(finalJwt, StandardCharsets.UTF_8)));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }

  private ResponseEntity<Void> redirectWithError(String baseUrl, String errorCode) {
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create(baseUrl + "/?switchError=" + errorCode));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }
}
