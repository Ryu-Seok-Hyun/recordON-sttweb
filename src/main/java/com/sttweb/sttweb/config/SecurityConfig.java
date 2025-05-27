package com.sttweb.sttweb.config;

import com.sttweb.sttweb.jwt.CustomAccessDeniedHandler;
import com.sttweb.sttweb.jwt.JwtAuthenticationEntryPoint;
import com.sttweb.sttweb.jwt.JwtAuthenticationFilter;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtAuthenticationEntryPoint jwtEntryPoint;
  private final CustomAccessDeniedHandler accessDeniedHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // 1) CSRF 비활성, 세션은 Stateless
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 2) 권한 설정
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/members/confirm-password").permitAll()
            .requestMatchers(
                "/api/members/signup",
                "/api/members/login",
                "/api/members/logout",
                "/api/user-permissions/**",
                "/api/test/**"
            ).permitAll()
            .anyRequest().authenticated()
        )

        // 3) JWT 필터 등록
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider, jwtEntryPoint),
            UsernamePasswordAuthenticationFilter.class
        )

        // 4) 401 / 403 응답 커스터마이징
        .exceptionHandling(ex -> ex
            // 토큰 없거나 만료 시 401 처리
            .authenticationEntryPoint(jwtEntryPoint)
            // 권한 없거나 재인증 필요 시 403 처리
            .accessDeniedHandler(accessDeniedHandler)
        )

        // 5) /logout 엔드포인트 처리
        .logout(ld -> ld
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
        )
    ;

    return http.build();
  }
}
