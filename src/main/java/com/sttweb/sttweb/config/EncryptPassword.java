package com.sttweb.sttweb.config;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.util.text.AES256TextEncryptor;
import org.jasypt.util.text.BasicTextEncryptor;

public class EncryptPassword {
  public static void main(String[] args) {
    StandardPBEStringEncryptor enc = new StandardPBEStringEncryptor();
    enc.setAlgorithm("PBEWithMD5AndDES");       // application.properties 와 동일하게
    enc.setPassword("070qlvmf1!");              // VM 옵션으로 넘기는 비밀번호
    String cipher = enc.encrypt("enxl zqul ugzr mwvf");
    System.out.println("ENC(" + cipher + ")");
  }
}