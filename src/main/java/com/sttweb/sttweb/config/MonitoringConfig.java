// src/main/java/com/sttweb/sttweb/config/MonitoringConfig.java
package com.sttweb.sttweb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;


@Configuration
public class MonitoringConfig {
  @Bean(name = "taskScheduler")
  public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
    ts.setPoolSize(1); // 반드시 1
    ts.setThreadNamePrefix("monitor-");
    return ts;
  }
}

