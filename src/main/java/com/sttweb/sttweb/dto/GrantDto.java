package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 라인 기반 권한 부여/회수 시 사용하는 DTO
 * • granteeUserId : 권한을 받을 사용자 ID
 * • lineId        : 권한을 받을 대상 라인 ID
 * • permLevel     : 부여할 권한 레벨 (1=권한 없음, 2=조회, 3=조회+청취, 4=조회+청취+다운로드)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrantDto {
  private Integer memberSeq;
  private Integer lineId;
  private Integer permLevel;
}
