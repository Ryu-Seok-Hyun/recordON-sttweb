package com.sttweb.sttweb.config;

import com.sttweb.sttweb.jwt.JwtAuthenticationFilter;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        .csrf(csrf -> csrf.disable())
        // 1) 로그인/회원가입/로그아웃은 모두 열어두고
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/members/signup", "/api/members/login", "/api/members/logout").permitAll()
            .anyRequest().authenticated()
        )
        // 2) 세션 대신 Stateless JWT 사용
        .sessionManagement(sm -> sm
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        // 3) JWT 토큰 파싱 필터를 UsernamePasswordAuthenticationFilter 앞에 등록
        .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class)
        // 4) (선택) 로그아웃 URL을 지정 — 기본은 /logout 이지만, 필요시 바꿀 수 있습니다.
        .logout(logout -> logout
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler((req, res, auth) -> {
              // 클라이언트에 성공 응답만 보내고, 서버에선 추가 작업이 없으면 이 부분만으로 충분합니다.
              res.setStatus(HttpServletResponse.SC_OK);
            })
        );

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }
}
