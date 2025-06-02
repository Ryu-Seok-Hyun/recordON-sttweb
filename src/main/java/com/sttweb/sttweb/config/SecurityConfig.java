package com.sttweb.sttweb.config;

import com.sttweb.sttweb.jwt.CustomAccessDeniedHandler;
import com.sttweb.sttweb.jwt.JwtAuthenticationEntryPoint;
import com.sttweb.sttweb.jwt.JwtAuthenticationFilter;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtAuthenticationEntryPoint jwtEntryPoint;
  private final CustomAccessDeniedHandler accessDeniedHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // 1) CORS 설정 먼저
        .cors(Customizer.withDefaults())

        // 2) CSRF 비활성화 & 세션 Stateless
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 3) URL별 접근 권한 설정
        .authorizeHttpRequests(auth -> auth
            // 프리플라이트 요청 허용
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // 로그인, 회원가입, 로그아웃, 패스워드 확인 API 등 인증 없이 호출 가능
            .requestMatchers(
                "/api/members/signup",
                "/api/members/login",
                "/api/members/logout",
                "/api/members/confirm-password",
                "/api/user-permissions/**",
                "/api/test/**"
            ).permitAll()

            // 그 외 경로는 모두 인증 필요
            .anyRequest().authenticated()
        )

        // 4) JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider, jwtEntryPoint),
            UsernamePasswordAuthenticationFilter.class
        )

        // 5) 인증/인가 실패 시 핸들러
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(jwtEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
        )

        // 6) 로그아웃 설정 (optional)
        .logout(ld -> ld
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
        );

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    // ✔ 여기서 반드시 React(또는 프론트)가 동작 중인 Origin을 허용해야 합니다.
    config.setAllowedOrigins(List.of(
        "http://localhost:39080",
        "http://localhost:39090",  // React 개발 서버가 39090에서 띄워진다면 추가
        "http://localhost:5173"    // 만약 Vite, 다른 포트에서도 테스트한다면 여기도 추가
    ));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
