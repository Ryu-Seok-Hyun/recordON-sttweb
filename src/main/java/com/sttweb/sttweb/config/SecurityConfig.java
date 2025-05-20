package com.sttweb.sttweb.config;

import com.sttweb.sttweb.jwt.JwtAuthenticationFilter;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
  private final JwtTokenProvider jwtTokenProvider;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // 1) stateless + CSRF off
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 2) URL별 권한 설정
        .authorizeHttpRequests(auth -> auth

            // -- 회원가입 / 로그인 / 로그아웃
            .requestMatchers(
                "/api/members/signup",
                "/api/members/login",
                "/api/members/logout"
            ).permitAll()

            // -- 권한 목록 조회
            .requestMatchers(HttpMethod.GET, "/api/roles", "/api/roles/**")
            .permitAll()

            // ── 지점 관리 ──

            // 브랜치 리스트 조회는 ADMIN 또는 USER 모두 허용
            .requestMatchers(HttpMethod.GET, "/api/branches")
            .hasAnyRole("ADMIN","USER")

            // 단건 조회도 ADMIN 또는 USER 허용
            .requestMatchers(HttpMethod.GET, "/api/branches/*")
            .hasAnyRole("ADMIN","USER")

            // 나머지 브랜치 API (POST, PUT, DELETE, activate 등)는 ADMIN만
            .requestMatchers("/api/branches/**")
            .hasRole("ADMIN")

            // ── 그 외 API 설정 생략 ──

            // 내 권한 조회
            .requestMatchers(HttpMethod.GET, "/api/members/me/role")
            .authenticated()

            // 사용자 권한 변경
            .requestMatchers(HttpMethod.PUT, "/api/members/*/role")
            .hasRole("ADMIN")

            // 녹취 API 등...
            .anyRequest()
            .authenticated()
        )

        // 3) JWT 필터 등록
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class
        )

        // 4) 401/403 메시지 커스터마이징
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((req, res, e) -> {
              res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
              res.setContentType("text/plain;charset=UTF-8");
              res.getWriter().write("토큰이 없습니다.");
            })
            .accessDeniedHandler((req, res, e) -> {
              res.setStatus(HttpServletResponse.SC_FORBIDDEN);
              res.setContentType("text/plain;charset=UTF-8");
              res.getWriter().write("권한이 없습니다.");
            })
        )

        // 5) 로그아웃 처리
        .logout(ld -> ld
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler((req, res, auth) ->
                res.setStatus(HttpServletResponse.SC_OK)
            )
        )
    ;

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }
}
