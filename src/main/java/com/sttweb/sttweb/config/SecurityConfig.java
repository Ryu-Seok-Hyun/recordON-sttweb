// src/main/java/com/sttweb/sttweb/config/SecurityConfig.java
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http

        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))


        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/members/signup",
                "/api/members/login",
                "/api/members/logout",
                "/api/user-permissions/**",
                "/api/test/**"
            ).permitAll()
            .anyRequest().authenticated()
        )

        // JWT 필터 등록
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class
        )

        // 401/403 메시지 커스터마이징
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
}
