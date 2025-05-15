// src/main/java/com/sttweb/sttweb/dto/GrantDto.java
package com.sttweb.sttweb.dto;

import lombok.Data;

@Data
public class GrantDto {
  private String granteeUserId;   // 권한을 받을 사용자 user_id
  private String targetUserId;    // 권한 대상 사용자 user_id
  private Integer permLevel;      // 1="권한 없음", 2=조회, 3=조회+청취, 4=조회+청취+다운로드
}
