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
        // CSRF 끄기, 세션 없이 stateless
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // URL별 권한 설정
        .authorizeHttpRequests(auth -> auth
            // 1) 회원가입/로그인/로그아웃 → 모두 허용
            .requestMatchers(
                "/api/members/signup",
                "/api/members/login",
                "/api/members/logout"
            ).permitAll()

            // 2) 권한 목록 조회 → 모두 허용
            .requestMatchers(HttpMethod.GET, "/api/roles", "/api/roles/**")
            .permitAll()

            // 3) branches/** → ADMIN 권한만
            .requestMatchers("/api/branches/**")
            .hasRole("ADMIN")

            // 4) 내 권한 조회 → 인증만 있으면 OK
            .requestMatchers(HttpMethod.GET, "/api/members/me/role")
            .authenticated()

            // 5) 다른 사용자 권한 변경 → ADMIN 권한만
            .requestMatchers(HttpMethod.PUT, "/api/members/*/role")
            .hasRole("ADMIN")

            // ───────────────────────────────────────
            // 6) 녹취 API 전체 → 스프링 시큐리티 단계에선 열어두고,
            //    컨트롤러 내부에서 401/403 직접 처리
            .requestMatchers("/api/records", "/api/records/**")
            .permitAll()
            // ───────────────────────────────────────

            // 7) 그 외 모든 요청 → 토큰만 있으면 OK
            .anyRequest()
            .authenticated()
        )

        // JWT 필터 등록
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class
        )

        // 401/403 응답 메시지 커스터마이징
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((req, res, ex2) -> {
              res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
              res.setContentType("text/plain;charset=UTF-8");
              res.getWriter().write("토큰이 없습니다.");
            })
            .accessDeniedHandler((req, res, ex2) -> {
              res.setStatus(HttpServletResponse.SC_FORBIDDEN);
              res.setContentType("text/plain;charset=UTF-8");
              res.getWriter().write("권한이 없습니다.");
            })
        )

        // 로그아웃 커스터마이징
        .logout(logout -> logout
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler((req, res, auth) ->
                res.setStatus(HttpServletResponse.SC_OK))
        )
    ;
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }
}
