package com.sttweb.sttweb.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BranchSwitchService {

  // 공유되는 시크릿. 기존 JwtTokenProvider와 동일하게 설정되어 있어야 함.
  private final JwtTokenProvider jwtTokenProvider;

  // 중복 사용 방지용 nonce 캐시 (한 번 쓰면 폐기)
  private final Cache<String, Boolean> usedNonces = Caffeine.newBuilder()
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .build();

  // 스위치 토큰 만료 초 (예: 30초)
  private static final long SWITCH_TOKEN_EXP_SECONDS = 30;

  public String createSwitchToken(String originalJwt, String userId, Integer targetBranchSeq) {
    String nonce = UUID.randomUUID().toString();

    Instant now = Instant.now();
    Instant exp = now.plusSeconds(SWITCH_TOKEN_EXP_SECONDS);

    return Jwts.builder()
        .setSubject(userId)
        .claim("type", "branch_switch")
        .claim("targetBranchSeq", targetBranchSeq)
        .claim("origToken", originalJwt) // 필요시 검증/감사용
        .claim("nonce", nonce)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(exp))
        .setId(UUID.randomUUID().toString())
        .signWith(jwtTokenProvider.getSigningKey(), SignatureAlgorithm.HS256)
        .compact();
  }

  public BranchSwitchPayload validateAndConsume(String switchToken) {
    Claims claims = Jwts.parserBuilder()
        .setSigningKey(jwtTokenProvider.getSigningKey())
        .build()
        .parseClaimsJws(switchToken)
        .getBody();

    String type = claims.get("type", String.class);
    if (!"branch_switch".equals(type)) {
      throw new IllegalArgumentException("유효하지 않은 스위치 토큰 타입");
    }

    String nonce = claims.get("nonce", String.class);
    if (nonce == null || usedNonces.getIfPresent(nonce) != null) {
      throw new IllegalArgumentException("이미 사용됐거나 잘못된 nonce");
    }

    // 사용 처리 (한 번만)
    usedNonces.put(nonce, Boolean.TRUE);

    String userId = claims.getSubject();
    Integer targetBranchSeq = claims.get("targetBranchSeq", Integer.class);
    String origToken = claims.get("origToken", String.class);

    return new BranchSwitchPayload(userId, targetBranchSeq, origToken);
  }

  public static record BranchSwitchPayload(String userId, Integer targetBranchSeq, String originalToken) {}
}
