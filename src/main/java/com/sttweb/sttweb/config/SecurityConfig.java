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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtAuthenticationEntryPoint jwtEntryPoint;
  private final CustomAccessDeniedHandler accessDeniedHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // 1) CORS 설정을 SecurityFilterChain에 연결
        //    아래에서 정의한 corsConfigurationSource() 빈을 사용하여
        //    'http://localhost:5173' 을 포함한 모든 Origin을 허용하도록 함
        .cors(Customizer.withDefaults())

        // 2) CSRF 비활성화 & 세션 Stateless
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 3) URL별 접근 권한 설정
        .authorizeHttpRequests(auth -> auth
            // 프리플라이트 요청(OPTIONS)은 무조건 허용
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // 회원가입, 로그인, 로그아웃, 비밀번호 확인 등은 인증 없이 호출 가능
            .requestMatchers(
                "/api/members/signup",
                "/api/members/login",
                "/api/members/logout",
                "/api/members/confirm-password",
                "/api/user-permissions/**",
                "/api/test/**",
                "/api/ini/download",
                "/api/ini/regenerate",
                "/api/perm/**"
            ).permitAll()

            // 그 외 모든 엔드포인트는 인증 필요
            .anyRequest().authenticated()
        )

        // 4) JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider, jwtEntryPoint),
            UsernamePasswordAuthenticationFilter.class
        )

        // 5) 인증/인가 실패 시 핸들러 등록
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

  /**
   * CORS 정책을 정의하는 빈.
   *
   * - setAllowedOriginPatterns(Arrays.asList("*")) 처럼 와일드카드(*)를 사용할 수도 있고,
   *   개발 중이라면 "http://localhost:5173" 만 허용하도록 명시할 수도 있습니다.
   *
   * - setAllowCredentials(true) 를 설정해 두면, Authorization 헤더나 Cookie 등의
   *   자격 증명도 함께 허용됩니다.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    // 1) 허용할 Origin 목록
    //    개발 환경 예시: 모든 Origin 허용
    config.setAllowedOriginPatterns(Arrays.asList("*"));
    //   or
    // config.setAllowedOrigins(Arrays.asList("http://localhost:5173"));

    // 2) 허용할 HTTP 메서드
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

    // 3) 허용할 요청 헤더
    config.setAllowedHeaders(Arrays.asList("*"));

    // 4) 자격 증명(Cookies, Authorization 헤더 등) 허용
    config.setAllowCredentials(true);

    // 5) 노출할 응답 헤더 (예: 프론트에서 Authorization 헤더를 읽어야 한다면)
    config.setExposedHeaders(Arrays.asList("Authorization"));

    // 6) 모든 경로에 위 CORS 규칙 적용
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return source;
  }
}
