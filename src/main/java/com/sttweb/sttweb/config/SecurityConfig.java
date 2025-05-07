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

  // SecurityConfig.java
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm ->
            sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .authorizeHttpRequests(auth -> auth
            // 회원 REST는 누구나
            .requestMatchers("/api/members/signup", "/api/members/login", "/api/members/logout")
            .permitAll()
            // branches/** 은 ADMIN 권한만
            .requestMatchers(HttpMethod.GET,  "/api/branches/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/branches").     hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT,  "/api/branches/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE,"/api/branches/**").hasRole("ADMIN")
            // 그 외는 인증만
            .anyRequest().authenticated()
        )
        // JWT 파싱 필터
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class
        )
        // 401/403 메시지 직접 제어
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
        // 로그아웃
        .logout(logout -> logout
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
        );

    return http.build();
  }


  @Bean
  public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }
}
