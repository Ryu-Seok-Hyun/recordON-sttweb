package com.sttweb.sttweb.config;

// 해쉬 첫 비번 만들기
public class HashGen {
  public static void main(String[] args) {
    System.out.println(new org.springframework.security.crypto.bcrypt
        .BCryptPasswordEncoder()
        .encode("1234"));
  }
}

// HashGen.java 파일의 전체 내용입니다. 이 코드로 교체해주세요.


//import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
//import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
//import java.util.Scanner;
//
//public class HashGen {
//
//  public static void main(String[] args) {
//    // 1. 암호화 설정 (application.properties와 완벽하게 동일해야 함)
//    PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
//    SimpleStringPBEConfig config = new SimpleStringPBEConfig();
//
//    // [수정] VM Option(-D...)에서 암호화 키를 가져오도록 변경
//    String password = System.getProperty("jasypt.encryptor.password");
//    if (password == null || password.isEmpty()) {
//      System.out.println("오류: VM Option에 '-Djasypt.encryptor.password=당신의키' 가 설정되지 않았습니다.");
//      return;
//    }
//
//    config.setPassword(password); // 암호화 키
//    config.setAlgorithm("PBEWithMD5AndDES");        // 알고리즘
//    config.setKeyObtentionIterations("1000");       // 해싱 반복 횟수
//    config.setPoolSize("1");                        // 인스턴스 풀
//    config.setProviderName("SunJCE");
//    config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator"); // Salt 생성 클래스
//    config.setStringOutputType("base64");           // 인코딩 방식
//    encryptor.setConfig(config);
//
//    // 2. 사용자로부터 암호화할 원본 문자열 입력받기
//    Scanner scanner = new Scanner(System.in);
//    System.out.print("암호화할 문자열을 입력하세요: ");
//    String plainText = scanner.nextLine();
//    scanner.close();
//
//    // 3. 암호화 후 결과 출력
//    String encryptedText = encryptor.encrypt(plainText);
//    System.out.println("----------------------------------------");
//    System.out.println("암호문: " + encryptedText);
//    System.out.println("----------------------------------------");
//  }
