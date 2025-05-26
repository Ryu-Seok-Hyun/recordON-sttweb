package com.sttweb.sttweb.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FilterInspector {

  @Autowired
  private ServletContext servletContext;

  @PostConstruct
  public void logFilters() {
    Map<String, ? extends FilterRegistration> registrations = servletContext.getFilterRegistrations();
    System.out.println("=== 등록된 필터 목록 ===");
    registrations.forEach((name, reg) -> {
      System.out.println("필터 이름: " + name + ", 클래스: " + reg.getClassName());
    });
  }
}