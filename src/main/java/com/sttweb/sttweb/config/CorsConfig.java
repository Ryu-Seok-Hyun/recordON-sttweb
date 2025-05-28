package com.sttweb.sttweb.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.ModelAndView;

@Configuration
public class CorsConfig {

  // 프론트엔트 cors 에러 관련.
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(Arrays.asList(
        "http://localhost:5173", "http://localhost:39090"
    ));
    configuration.setAllowedOriginPatterns(List.of("*")); // 또는 frontend 주소 지정
    configuration.setAllowCredentials(true);
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
  }

  @Bean
  public ErrorViewResolver redirectToIndex() {
    return (HttpServletRequest request, HttpStatus status, Map<String, Object> model) -> {
      if (status == HttpStatus.NOT_FOUND) {
        return new ModelAndView("redirect:/index.html", Collections.emptyMap(), HttpStatus.OK);
      }
      return null;
    };
  }
}