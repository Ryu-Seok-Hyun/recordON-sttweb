// src/main/java/com/sttweb/sttweb/config/ShedLockConfig.java

package com.sttweb.sttweb.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ShedLockConfig {
  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withTableName("shedlock")
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build()
    );
  }
}
