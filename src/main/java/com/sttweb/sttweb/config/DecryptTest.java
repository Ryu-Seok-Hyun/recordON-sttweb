package com.sttweb.sttweb.config;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.iv.NoIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

public class DecryptTest {
  public static void main(String[] args) {
    String encryptedText = "1ay9rp+mKJMeH3WllY73hCkJwYySqwFX";

    PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
    SimpleStringPBEConfig config = new SimpleStringPBEConfig();

    config.setPassword("070qlvmf1!"); // 복호화 키
    config.setAlgorithm("PBEWithMD5AndDES");
    config.setKeyObtentionIterations("1000");
    config.setPoolSize("1"); // 중요
    config.setProviderName("SunJCE");
    config.setSaltGeneratorClassName(RandomSaltGenerator.class.getName());
    config.setStringOutputType("base64");
    config.setIvGeneratorClassName(NoIvGenerator.class.getName());

    encryptor.setConfig(config);

    String decryptedText = encryptor.decrypt(encryptedText);
    System.out.println("복호화 결과: " + decryptedText);
  }
}


