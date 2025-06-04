// src/main/java/com/sttweb/sttweb/dto/MemberLinePermDto.java
package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberLinePermDto {
  private Integer       memberSeq;       // 권한을 부여받은 회원 PK
  private String        userId;          // 권한을 부여받은 회원의 아이디
  private String        userName;        // 권한을 부여받은 회원의 이름(또는 userId)
  private Integer       branchSeq;       // 권한을 부여 받은 “회원”의 지사번호
  private Integer       lineId;          // 회선 ID
  private String        callNum;         // 내선번호
  private String        callOwnerName;   // 해당 내선을 사용하는(소유) 회원의 이름
  private Integer       roleSeq;         // 권한 시퀀스
  private String        roleCode;        // 권한 코드
  private String        roleDescription; // 권한 설명
  private LocalDateTime regtime;         // 권한이 등록된 시각
}
