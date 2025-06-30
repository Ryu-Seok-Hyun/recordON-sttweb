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
    // 로그인 전·후 필터
    var loginFilter  = new LoginAccessFilter(branchSvc, memberSvc);
    var jwtFilter    = new JwtAuthenticationFilter(jwtTokenProvider, jwtEntryPoint);
    var branchFilter = new BranchGuardFilter(jwtTokenProvider, branchSvc);

    http
        .csrf(AbstractHttpConfigurer::disable)
        // WebMvcConfigurer 에 정의한 CORS 설정 적용
        .cors(Customizer.withDefaults())
        .sessionManagement(sm ->
            sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            // 프리플라이트(OPTIONS) 허용
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
        .addFilterBefore(loginFilter,  UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtFilter,    UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(branchFilter,   JwtAuthenticationFilter.class)
        .logout(ld -> ld
            .logoutUrl("/api/members/logout")
            .logoutSuccessHandler(
                (req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
        );

    return http.build();
  }
}
