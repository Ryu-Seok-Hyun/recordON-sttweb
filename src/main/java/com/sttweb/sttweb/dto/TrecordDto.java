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

  private Integer recordSeq;        // 녹취 일련번호
  private String callStartDateTime;  // 통화 시작 시각
  private String callEndDateTime;    // 통화 종료 시각
  private String audioPlayTime;      // 통화 시간(HH:mm:ss)
  private String ioDiscdVal;         // 수신/발신 구분
  private String number1;            // 내선 번호
  private String number2;            // 발신 번호
  private String audioFileDir;       // 오디오 파일 경로
  private String callStatus;         // 통화 상태 (OK, NO-ANS 등)
  private String regDate;            // 레코드 등록 시각 (YYYY-MM-DD HH:mm:ss)

  /** 이 녹취를 생성한 사용자(memberSeq) */
  private Integer ownerMemberSeq;

  /** 이 녹취가 발생한 지점(branchSeq) */
  private Integer branchSeq;

  /**
   * number2가 휴대폰 번호일 경우 중간 4자리(예: “01012345678” → “010****5678”)를
   * mask 처리하는 메서드
   */
  public void maskNumber2() {
    if (this.number2 == null) {
      return;
    }
    String raw = this.number2.trim();
    if (raw.length() >= 7) {
      String head = raw.substring(0, 3);
      String tail = raw.substring(raw.length() - 4);
      this.number2 = head + "****" + tail;
    }
  }
}
