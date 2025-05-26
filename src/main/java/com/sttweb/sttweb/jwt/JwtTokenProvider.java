// ✅ 변경사항 요약:
// 1. IP 기반 조회 시 IPv6 로컬주소 처리 추가
// 2. JWT 유효시간 기본값을 12시간으로 연장 (JwtTokenProvider 수정)
// 3. 토큰 생성 시 branchName을 포함해 응답 JSON에서 redirectUrl 제공

// === JwtTokenProvider.java ===
package com.sttweb.sttweb.jwt;

import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import com.sttweb.sttweb.dto.TmemberDto.Info;

@Slf4j
@Component
public class JwtTokenProvider {
  @Value("${jwt.secret}")
  private String secretKey;

  @Value("${jwt.expiration.time:43200000}") // 기본 12시간
  private long validityInMs;


  @PostConstruct
  protected void init() {
    secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
  }
  public String getUserLevel(String token) {
    return getRoles(token);
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
        .signWith(SignatureAlgorithm.HS256, secretKey)
        .compact();
  }



  public String createToken(String userId, String userLevel, Integer branchSeq) {
    Claims claims = Jwts.claims().setSubject(userId);
    claims.put("roles", userLevel);
    claims.put("branchSeq", branchSeq);
    Date now = new Date();
    Date expiry = new Date(now.getTime() + validityInMs);

    return Jwts.builder()
        .setClaims(claims)
        .setIssuedAt(now)
        .setExpiration(expiry)
        .signWith(SignatureAlgorithm.HS256, secretKey)
        .compact();
  }

  public String getUserId(String token) {
    return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
  }

  public String getRoles(String token) {
    return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().get("roles", String.class);
  }

  public Integer getBranchSeq(String token) {
    return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().get("branchSeq", Integer.class);
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.warn("JWT 만료됨", e);
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("JWT 유효하지 않음", e);
    }
    return false;
  }
}