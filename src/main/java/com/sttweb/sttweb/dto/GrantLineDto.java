// src/main/java/com/sttweb/sttweb/dto/GrantLineDto.java
package com.sttweb.sttweb.dto;

import lombok.Data;

/**
 * 회원↔회선↔권한 매핑 부여/회수용 DTO
 *   • memberSeq : 회원 PK
 *   • lineId    : 회선(내선) PK
 *   • roleSeq   : 부여할 권한(role_seq) 번호
 */
@Data
public class GrantLineDto {
  private Integer memberSeq;
  private Integer lineId;
  private Integer roleSeq;
}
