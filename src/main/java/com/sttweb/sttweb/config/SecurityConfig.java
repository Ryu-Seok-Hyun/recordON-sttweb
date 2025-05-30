package com.sttweb.sttweb.config;

import com.sttweb.sttweb.jwt.CustomAccessDeniedHandler;
import com.sttweb.sttweb.jwt.JwtAuthenticationEntryPoint;
import com.sttweb.sttweb.jwt.JwtAuthenticationFilter;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
        // 1) WebMvcConfigurer 에서 정의한 CORS 정책 적용
        .cors(Customizer.withDefaults())
        // 2) CSRF 비활성화 & 세션은 STATELESS
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 3) 요청별 권한 설정
        .authorizeHttpRequests(auth -> auth
            // 3.1) 프리플라이트 요청은 무조건 통과
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // 3.2) 로그인·회원가입 등 인증 없이 허용
            .requestMatchers(
                "/api/members/signup",
                "/api/members/login",
                "/api/members/logout",
                "/api/members/confirm-password",
                "/api/user-permissions/**",
                "/api/test/**"
            ).permitAll()

            // 3.3) 그 외 요청은 모두 인증 필요
            .anyRequest().authenticated()
        )

        // 4) JWT 필터 등록 (위 permitAll URL들은 필터에서 건너뛸 겁니다)
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider, jwtEntryPoint),
            UsernamePasswordAuthenticationFilter.class
        )

        // 5) 401/403 에러 핸들러
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(jwtEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
        )

        // 6) 로그아웃 처리
        .logout(ld -> ld
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler((req, res, auth) ->
                res.setStatus(HttpServletResponse.SC_OK))
        );

    return http.build();
  }
}
