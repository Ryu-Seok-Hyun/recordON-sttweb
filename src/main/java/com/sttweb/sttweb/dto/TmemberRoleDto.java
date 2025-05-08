package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmemberRoleDto {
  private Integer roleSeq;// 1=NONE,2=READ,3=LISTEN,4=DOWNLOAD
  private String roleCode; // NONE, READ, LISTEN, DOWNLOAD
  private String description;  // 권한 설명
}