package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionViewDto {

  private Integer lineId;          // 라인 ID
  private String callNum;          // 라인 번호
  private boolean canRead;         // 조회 가능
  private boolean canListen;       // 청취 가능
  private boolean canDownload;     // 다운로드 가능
  private LocalDateTime crtime;    // 생성 시간
}