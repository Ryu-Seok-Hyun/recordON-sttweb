// src/main/java/com/sttweb/sttweb/dto/GrantDto.java
package com.sttweb.sttweb.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 사용자 간 권한 부여/회수 시 사용하는 DTO
 * • granteeUserId : 권한을 받을 사용자 ID
 * • targetUserId  : 권한을 받을 대상 사용자 ID
 * • permLevel     : 부여할 권한 레벨 (1=권한 없음, 2=조회, 3=조회+청취, 4=조회+청취+다운로드)
 */
@Builder
@Data
public class GrantDto {
  private String granteeUserId;
  private String targetUserId;
  private Integer permLevel;
}
