package com.sttweb.sttweb.jwt;

import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;

/**
 * JWT 토큰 생성 및 파싱을 담당하는 컴포넌트
 */
@Component
public class JwtTokenProvider {
  @Value("${jwt.secret}")
  private String secretKey;

  @Value("${jwt.expiration.time}")
  private long validityInMs;

  @PostConstruct
  protected void init() {
    // Base64로 인코딩
    secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
  }

  /**
   * 토큰 생성 (Subject: userId, Claim: roles)
   */
  public String createToken(String userId, String roles) {
    Claims claims = Jwts.claims().setSubject(userId);
    claims.put("roles", roles);
    Date now    = new Date();
    Date expiry = new Date(now.getTime() + validityInMs);

    return Jwts.builder()
        .setClaims(claims)
        .setIssuedAt(now)
        .setExpiration(expiry)
        .signWith(SignatureAlgorithm.HS256, secretKey)
        .compact();
  }

  /**
   * 토큰에서 userId 추출
   */
  public String getUserId(String token) {
    return Jwts.parser()
        .setSigningKey(secretKey)
        .parseClaimsJws(token)
        .getBody()
        .getSubject();
  }

  /**
   * 토큰에서 roles (userLevel) 추출
   */
  public String getRoles(String token) {
    return Jwts.parser()
        .setSigningKey(secretKey)
        .parseClaimsJws(token)
        .getBody()
        .get("roles", String.class);
  }

  /**
   * JWT 토큰에서 userLevel과 동일하게 roles를 반환
   */
  public String getUserLevel(String token) {
    return getRoles(token);
  }

  /**
   * 토큰 유효성 검사
   */
  public boolean validateToken(String token) {
    try {
      Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}
