// src/main/java/com/sttweb/sttweb/config/GlobalCorsConfig.java
package com.sttweb.sttweb.config;

import java.util.List;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import org.springframework.boot.web.servlet.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GlobalCorsConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOriginPatterns("*")
        .allowedMethods("*")
        .allowedHeaders("*")
        .exposedHeaders(
            "Authorization",
            "Accept-Ranges",
            "Content-Range",
            "Content-Length"
        )
        .allowCredentials(true);
  }
}
