package com.sttweb.sttweb.controller;

// 해쉬 첫 비번 만들기
public class HashGen {
  public static void main(String[] args) {
    System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("1234"));
  }
}
