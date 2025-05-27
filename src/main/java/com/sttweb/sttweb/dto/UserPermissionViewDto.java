package com.sttweb.sttweb.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserPermissionViewDto {
  private String userId;     // 대상 사용자 ID
  private boolean canRead;    // 조회 가능
  private boolean canListen;  // 청취 가능
  private boolean canDownload;// 다운로드 가능

  private LocalDateTime crtime;
}