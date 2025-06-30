package com.sttweb.sttweb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcCorsConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        // 수백 개의 호스트가 올 수 있으므로 와일드카드 사용
        .allowedOriginPatterns("*")
        .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
        .allowCredentials(true)
        .allowedHeaders("*")
        .exposedHeaders(
            "Authorization",
            "Accept-Ranges",
            "Content-Range",
            "Content-Length"
        )
        .maxAge(3600);
  }
}
