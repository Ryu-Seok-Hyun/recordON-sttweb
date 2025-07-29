package com.sttweb.sttweb.jwt;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenProvider {

  private Key secretKey;

  @Value("${jwt.secret}")
  private String secret;

  @Value("${jwt.expiration.time}")
  private long validityInMs;                       // 일반 JWT 유효시간 (ms)

  private static final long REAUTH_EXPIRE_MS = 30 * 60 * 1000L; // 재인증: 30분

  /*────────────────────────── INIT ──────────────────────────*/
  @PostConstruct
  protected void init() {
    byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(secret);
    this.secretKey  = Keys.hmacShaKeyFor(keyBytes);
  }

  /*────────────────── 1) 일반 토큰 생성 ───────────────────*/
  /**
   * 사용자 Info 객체의 모든 주요 필드를 claim 으로 넣고 토큰을 생성합니다.
   */
  public String createTokenFromInfo(Info info) {
    Claims claims = Jwts.claims().setSubject(info.getUserId());

    // 기본 정보
    claims.put("memberSeq",  info.getMemberSeq());
    claims.put("branchSeq",  info.getBranchSeq());
    claims.put("branchName", info.getBranchName());
    claims.put("userLevel",  info.getUserLevel());        // ★ HQ 여부 판단용
    claims.put("number",     info.getNumber());
    claims.put("hqYn",       info.getHqYn());
    claims.put("currentBranchSeq",  info.getCurrentBranchSeq());
    claims.put("currentBranchName", info.getCurrentBranchName());
    claims.put("mustChangePassword", info.getMustChangePassword());
    claims.put("position",   info.getPosition());
    claims.put("maskFlag",   info.getMaskFlag());
    claims.put("rank",       info.getRank());
    claims.put("department", info.getDepartment());
    claims.put("discd",      info.getDiscd());
    claims.put("crtime",     info.getCrtime());
    claims.put("udtime",     info.getUdtime());
    claims.put("reguserId",  info.getReguserId());
    claims.put("role_seq",   info.getRoleSeq());

    // 현재 지사 전환(멀티지사 로그인) 시 사용
    claims.put("allowedBranchSeq",
        Optional.ofNullable(info.getCurrentBranchSeq())
            .orElse(info.getBranchSeq()));

    /* 필요하다면 roles(권한 문자열 리스트)도 같이 넣어두세요.
       ex) claims.put("roles", info.getRoles()); */

    Date now    = new Date();
    Date expiry = new Date(now.getTime() + validityInMs);

    return Jwts.builder()
        .setClaims(claims)
        .setIssuedAt(now)
        .setExpiration(expiry)
        .signWith(secretKey, SignatureAlgorithm.HS256)
        .compact();
  }

  /*────────────────── 2) 재인증(reauth) 토큰 ───────────────────*/
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

  /*────────────────── 3) 토큰 검증 메서드 ───────────────────*/
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
  /*────────────────────────────────────────────────────────*/

  // ─────────────────── 3‑1) 토큰 파싱 헬퍼 ───────────────────
  /**
   * 토큰을 파싱해 Claims를 반환합니다.
   * extractToken() 등에서 유효·만료 검증용으로 호출합니다.
   */
  public Claims parseClaims(String token) throws ExpiredJwtException, JwtException {
    return Jwts.parserBuilder()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(token)
        .getBody();
  }
// ─────────────────────────────────────────────────────────


  /*────────────────── 4) 개별 Claim 추출 ───────────────────*/
  /** subject(userId) */
  public String getUserId(String token) {
    return getClaims(token).getSubject();
  }

  /** branchSeq */
  public Integer getBranchSeq(String token) {
    return getClaims(token).get("branchSeq", Integer.class);
  }

  /** ★ userLevel (HQ 여부 판단에 사용) */
  public String getUserLevel(String token) {             // ★ 추가
    return getClaims(token).get("userLevel", String.class);
  }

  /** (선택) roles 배열 */
  public List<String> getRoles(String token) {
    return getClaims(token).get("roles", List.class);
  }

  public Integer getCurrentBranchSeq(String token){
    return getClaims(token).get("currentBranchSeq", Integer.class);
  }
  public String getCurrentBranchName(String token){
    return getClaims(token).get("currentBranchName", String.class);
  }

  /*────────────────── 5) 내부 헬퍼 ───────────────────*/
  private Claims getClaims(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(token)
        .getBody();
  }
}
