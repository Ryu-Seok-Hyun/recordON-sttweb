package com.sttweb.sttweb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;

@Configuration
public class ResourceConfig {
  @Bean
  public ResourceRegionHttpMessageConverter resourceRegionHttpMessageConverter() {
    return new ResourceRegionHttpMessageConverter();
  }
}
