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

  /**
   * number2가 10~11자리 휴대폰 번호라고 가정하고
   * 중간 4자리를 **** 로 마스킹하는 메서드
   * (ex: 01012345678 → 010****5678)
   */
  public void maskNumber2() {
    if (this.number2 == null) {
      return;
    }
    // 예시: 휴대폰 번호가 10~11자리(예: "01012345678")라면,
    // 맨 앞 3자리 + "****" + 뒤 4자리 로 바꾼다.
    String raw = this.number2.trim();
    // 숫자만 들어온다고 가정한 뒤, 길이가 10 이상인 경우에만 마스킹
    if (raw.length() >= 7) {
      String head = raw.substring(0, 3);
      String tail = raw.substring(raw.length() - 4);
      this.number2 = head + "****" + tail;
    }
    // 만약 7자리 미만이거나 다른 형식이면, 원래 숫자를 그대로 둡니다.
  }
}


