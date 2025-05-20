package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrecordDto {

  private Integer recordSeq; // 녹취 기록 일련번호(PK)
  private String callStartDateTime; // 통화 시작시각 (YYYY-MM-DD HH:mm:ss)
  private String callEndDateTime; // 통화 종료시각(YYYY-MM-DD HH:mm:ss)
  private String audioPlayTime; // 오디오 재생 시간(HH:mm:ss) = 통화시간
  private String ioDiscdVal; //수신,발신
  private String number1; // 수신된 내선번호
  private String number2; // 발신 번호 전체
  private String audioFileDir; // 오디오 파일 경로
  private String callStatus; // 통화 상태 (OK,NO-ANS)
  private String regDate; //등록 날짜 (YYYY-MM-DD HH:mm:ss)

  /** 이 녹취를 생성한 사용자(memberSeq) */
  private Integer ownerMemberSeq;
  /** 이 녹취가 속한 지점(branchSeq) */
  private Integer branchSeq;
}


