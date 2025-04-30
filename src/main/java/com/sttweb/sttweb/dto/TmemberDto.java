package com.sttweb.sttweb.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmemberDto {

  private Integer memberSeq; // 회원 일련번호(PK)
  private Integer branchSeq; // 지점 일련번호(FK)
  private Integer employeeId; // 사원 아이디
  private String userId; // 사용자 아이디
  private String userPass; // 사용자 비밀번호
  private String adminYn; // 관리자여부(0/1)
  private String number; // 내선번호
  private Integer discd; // 삭제 여부
  private String crtime; // 생성시간(YYYY-MM-DD HH:mm:ss)
  private String reguserId; // 등록자 아이디
}


