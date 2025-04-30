package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TactivitylogDto {

  private Integer activitySeq; // 활동 일련번호 (PK)
  private String type; // ∴ "type": "LOGIN"   // 대분류: 로그인 관련 이벤트, login 이냐 logout 이냐.
  private Integer branchSeq; //지점 일련번호 = NULL로 넣기
  private String companyName; // 회사명
  private Integer memberSeq; // 회사 일련번호
  private String userId; // 사용자 아이디
  private String activity; // 활동(?)(로그인, 로그아웃)
  private String contents; // 상세내용(뭐가 변경되었는지에 대한 내용), ∴ "activity": "LOGIN_SUCCESS"  // 세부내역: 로그인 성공
  private String dir; //  파일/디렉토리 경호
  private Integer employeeId; // 사원 아이디 = NULL롷 넣기
  private String pbIp; //IP 주소 = 공란으로 넣기
  private String pvIp; // IP주소
  private String crtime; // 생성 시간(YYYY-MM-DD HH:mm:ss)
  private Integer workerSeq; // 작업자 일련번호 ( 1 또는 2 )
  private String workerId; // 작업자 아이디 = userID 랑 같게함
}