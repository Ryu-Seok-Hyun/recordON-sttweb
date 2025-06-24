package com.sttweb.sttweb.config;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceConfig {

  private final DataSourceProperties props;
  private final StringEncryptor encryptor;

  public DataSourceConfig(DataSourceProperties props, StringEncryptor encryptor) {
    this.props = props;
    this.encryptor = encryptor;
  }

  @Bean
  public DataSource dataSource() {
    HikariConfig cfg = new HikariConfig();

    // [수정] 암호화된 문자열을 올바르게 복호화하는 유틸리티 메서드 사용
    cfg.setJdbcUrl(decryptValue(props.getUrl()));
    cfg.setUsername(decryptValue(props.getUsername()));
    cfg.setPassword(decryptValue(props.getPassword()));

    // 드라이버 설정은 그대로 유지
    cfg.setDriverClassName(props.getDriverClassName());

    return new HikariDataSource(cfg);
  }

  /**
   * [추가] "ENC(...)" 래퍼를 올바르게 처리하고 복호화하는 헬퍼 메서드
   * @param value application.properties에서 읽어온 원본 값
   * @return 복호화된 값 또는 원본 값
   */
  private String decryptValue(String value) {
    if (value != null && value.trim().toUpperCase().startsWith("ENC(") && value.trim().endsWith(")")) {
      // "ENC(" 와 ")" 사이의 암호화된 내용만 추출하여 복호화
      String encrypted = value.trim().substring(4, value.trim().length() - 1);
      return encryptor.decrypt(encrypted);
    }
    // "ENC(...)" 형태가 아니면 원본 값 그대로 반환
    return value;
  }
}