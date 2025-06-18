package com.sttweb.sttweb.config;

import com.sttweb.sttweb.filter.BranchGuardFilter;
import com.sttweb.sttweb.filter.LoginAccessFilter;
import com.sttweb.sttweb.jwt.CustomAccessDeniedHandler;
import com.sttweb.sttweb.jwt.JwtAuthenticationEntryPoint;
import com.sttweb.sttweb.jwt.JwtAuthenticationFilter;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
  private final TbranchService branchSvc;
  private final TmemberService memberSvc;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // 반드시 두 서비스 전달!
    LoginAccessFilter loginFilter = new LoginAccessFilter(branchSvc, memberSvc);

    JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenProvider, jwtEntryPoint);
    BranchGuardFilter branchFilter = new BranchGuardFilter(jwtTokenProvider, branchSvc);

    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(
                "/api/members/signup",
                "/api/members/login",
                "/api/members/logout",
                "/api/members/confirm-password",
                "/api/user-permissions/**",
                "/api/test/**",
                "/api/ini/**",
                "/api/perm/**"
            ).permitAll()
            .anyRequest().authenticated()
        )
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(jwtEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
        )
        // 로그인 전 필터(아무 body 작업 X)
        .addFilterBefore(loginFilter, UsernamePasswordAuthenticationFilter.class)
        // JWT 인증
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        // JWT 통과 후 지사 일치 검사
        .addFilterAfter(branchFilter, JwtAuthenticationFilter.class)
        .logout(ld -> ld
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
        );

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    // 모든 오리진, 모든 메서드, 모든 요청 헤더 허용
    cfg.setAllowedOriginPatterns(Arrays.asList("*"));
    cfg.setAllowedMethods(Arrays.asList("GET","POST","PUT","DELETE","OPTIONS"));
    cfg.setAllowedHeaders(Arrays.asList("*"));
    cfg.setAllowCredentials(true);

    // 여기서 스트리밍에 필요한 헤더들을 추가로 노출!
    cfg.setExposedHeaders(Arrays.asList(
        "Authorization",      // 기존
        "Accept-Ranges",      // 스트리밍 가능 바이트 범위
        "Content-Range",      // 실제 응답 바이트 범위
        "Content-Length"      // (선택) 전체 크기 확인용
    ));

    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    // 전 경로에 CORS 적용
    src.registerCorsConfiguration("/**", cfg);
    return src;
  }
}
