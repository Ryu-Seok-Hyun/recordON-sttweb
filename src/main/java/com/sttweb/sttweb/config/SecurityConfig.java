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
        // CSRF 끄고
        .csrf(csrf -> csrf.disable())
        // 세션 사용하지 않음
        .sessionManagement(sm -> sm
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        // URL별 권한 설정
        .authorizeHttpRequests(auth -> auth
            // 1) 회원가입/로그인/로그아웃은 모두 허용
            .requestMatchers(
                "/api/members/signup",
                "/api/members/login",
                "/api/members/logout"
            ).permitAll()

            // 2) 권한 리스트 조회는 누구나 허용
            .requestMatchers(HttpMethod.GET, "/api/roles", "/api/roles/**")
            .permitAll()

            // 3) branches/** 는 ADMIN 권한만
            .requestMatchers("/api/branches/**")
            .hasRole("ADMIN")

            // 4) 내 권한 조회는 로그인만 하면 OK
            .requestMatchers(HttpMethod.GET, "/api/members/me/role")
            .authenticated()

            // 5) 다른 사용자 권한 변경은 ADMIN 만
            .requestMatchers(HttpMethod.PUT, "/api/members/*/role")
            .hasRole("ADMIN")

            // 6) 나머지 요청은 토큰만 있으면 OK
            .anyRequest()
            .authenticated()
        )

        // JWT 필터를 스프링 시큐리티 필터 체인에 등록
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class
        )

        // 401/403 예외 메시지 모두 직접 내려줌
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

        // 로그아웃 엔드포인트 커스터마이징
        .logout(logout -> logout
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
