package com.sttweb.sttweb.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.security.Key;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import com.sttweb.sttweb.dto.TmemberDto.Info;

@Slf4j
@Component
public class JwtTokenProvider {

  private Key secretKey;

  @Value("${jwt.secret}")
  private String secret;

  @Value("${jwt.expiration.time}")
  private long validityInMs;             // 일반 JWT 유효시간 (ms)

  private static final long REAUTH_EXPIRE_MS = 30 * 60 * 1000L; // 재인증: 30분


  @PostConstruct
  protected void init() {
    // base64 디코딩된 시크릿 키로 HMAC-SHA256 키 생성
    byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(secret);
    this.secretKey    = Keys.hmacShaKeyFor(keyBytes);
  }

  /**
   * 사용자 Info 객체에 있는 모든 필드를 claim 으로 넣고 토큰을 생성합니다.
   */
  // Info 객체 타입으로 선언
  public String createTokenFromInfo(Info info) {
    Claims claims = Jwts.claims().setSubject(info.getUserId());
    // 모든 필드 claims 에 담기
    claims.put("memberSeq",   info.getMemberSeq());
    claims.put("branchSeq",   info.getBranchSeq());
    claims.put("branchName",  info.getBranchName());
    claims.put("employeeId",  info.getEmployeeId());
    claims.put("userLevel",   info.getUserLevel());
    claims.put("number",      info.getNumber());
    claims.put("position",     info.getPosition());
    claims.put("rank",       info.getRank());
    claims.put("department", info.getDepartment());
    claims.put("discd",       info.getDiscd());
    claims.put("crtime",      info.getCrtime());
    claims.put("udtime",      info.getUdtime());
    claims.put("reguserId",   info.getReguserId());
    claims.put("role_seq",    info.getRoleSeq());
    claims.put("mustChangePassword", info.getMustChangePassword());

    Date now    = new Date();
    Date expiry = new Date(now.getTime() + validityInMs);

    return Jwts.builder()
        .setClaims(claims)
        .setIssuedAt(now)
        .setExpiration(expiry)
        .signWith(secretKey, SignatureAlgorithm.HS256)
        .compact();
  }



  /**
   * 2) 재인증용 토큰: Payload에는 최소한의 subject(userId) 와
   *    reauth=true flag 만 담고, 만료시간 = now + REAUTH_EXPIRE_MS (30분)
   */
  public String createReAuthToken(String userId) {
    Date now    = new Date();
    Date expiry = new Date(now.getTime() + REAUTH_EXPIRE_MS);

    return Jwts.builder()
        .setSubject(userId)
        .claim("reauth", true)
        .setIssuedAt(now)
        .setExpiration(expiry)
        .signWith(secretKey, SignatureAlgorithm.HS256)
        .compact();
  }

  /** 일반 토큰 유효성 검사 */
  public boolean validateToken(String token) {
    try {
      Jwts.parserBuilder()
          .setSigningKey(secretKey)
          .build()
          .parseClaimsJws(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.warn("JWT 만료됨", e);
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("JWT 유효하지 않음", e);
    }
    return false;
  }

  /** 재인증용 토큰인지 확인하며, 만료되지 않았어야 함 */
  public boolean validateReAuthToken(String token) {
    try {
      Claims claims = Jwts.parserBuilder()
          .setSigningKey(secretKey)
          .build()
          .parseClaimsJws(token)
          .getBody();
      return Boolean.TRUE.equals(claims.get("reauth", Boolean.class));
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("ReAuth 토큰 유효하지 않음", e);
      return false;
    }
  }

  /** 토큰에서 userId(subject) 추출 */
  public String getUserId(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(token)
        .getBody()
        .getSubject();
  }

  /** (선택) 기존 roles, branchSeq 같은 개별 claim 접근 메서드도 유지 가능합니다 */
  public String getRoles(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(token)
        .getBody()
        .get("roles", String.class);
  }

  public Integer getBranchSeq(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(token)
        .getBody()
        .get("branchSeq", Integer.class);
  }
}