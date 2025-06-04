// src/main/java/com/sttweb/sttweb/dto/MemberLinePermDto.java
package com.sttweb.sttweb.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 회원 ↔ 회선 ↔ 권한 정보를 반환하기 위한 DTO
 *
 * • memberSeq     : 회원 PK
 * • userId        : 회원 ID (tmember.user_id)
 * • lineId        : 회선 PK (trecord_tel_list.id)
 * • callNum       : 회선(내선번호)
 * • roleSeq       : 권한 번호 (tmember_role.role_seq)
 * • roleCode      : 권한 코드 (NONE, READ, LISTEN, DOWNLOAD)
 * • roleDescription : 권한 설명
 * • regtime       : 이 매핑이 등록된 시각 (테이블에 저장된 시점)
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberLinePermDto {
  private Integer memberSeq;
  private String userId;
  private Integer lineId;
  private String callNum;
  private Integer roleSeq;
  private String roleCode;
  private String roleDescription;
  private LocalDateTime regtime;
}
