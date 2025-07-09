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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
    var loginFilter  = new LoginAccessFilter(branchSvc, memberSvc);
    var jwtFilter    = new JwtAuthenticationFilter(jwtTokenProvider, jwtEntryPoint);
    var branchFilter = new BranchGuardFilter(jwtTokenProvider, branchSvc);

    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            // preflight
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            // 로그인 페이지(GET) 및 실제 로그인 처리(POST) 허용
            .requestMatchers(HttpMethod.GET,  "/login").permitAll()
            .requestMatchers(HttpMethod.POST, "/login", "/api/members/login").permitAll()
            // 그 외 회원가입·로그아웃·확인·녹취 스트리밍
            .requestMatchers(
                "/api/members/signup",
                "/api/members/logout",
                "/api/members/confirm-password",
                "/api/records/**",
                "/records/**"
            ).permitAll()
            .anyRequest().authenticated()
        )
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(jwtEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
        )
        // 로그인 직전에 실행될 필터
        .addFilterBefore(loginFilter,  org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtFilter,    org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(branchFilter,   JwtAuthenticationFilter.class)
        .logout(ld -> ld
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
        )
    ;

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of(
        HttpHeaders.AUTHORIZATION,
        HttpHeaders.ACCEPT_RANGES,
        HttpHeaders.CONTENT_RANGE,
        HttpHeaders.CONTENT_LENGTH
    ));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
