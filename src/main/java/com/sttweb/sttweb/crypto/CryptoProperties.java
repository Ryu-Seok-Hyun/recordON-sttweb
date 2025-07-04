package com.sttweb.sttweb.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "crypto.aes")
public class CryptoProperties {

  /**
   * application.properties 에서
   * crypto.aes.key=BASE64_32BYTE_KEY
   * 로 넘어오는 값
   */
  private String key;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  /**
   * Base64로 인코딩된 AES 키를 SecretKey로 디코딩해서 반환
   */
  public SecretKey getSecretKey() {
    byte[] decoded = Base64.getDecoder().decode(this.key);
    return new SecretKeySpec(decoded, "AES");
  }
}
