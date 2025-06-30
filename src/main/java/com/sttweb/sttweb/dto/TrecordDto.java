package com.sttweb.sttweb.dto;

import com.sttweb.sttweb.entity.TrecordEntity;
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

  private String branchName;

  private Integer roleSeq;
  private Integer lineId;

  // ─────────── 여기에 두 필드를 추가 ───────────
  /** HQ 서버를 통해 접근할 수 있는 스트리밍 URL */
  private String listenUrl;
  /** HQ 서버를 통해 접근할 수 있는 다운로드 URL */
  private String downloadUrl;
  // ──────────────────────────────────────────

  public Integer getRoleSeq() {
    return roleSeq == null ? 0 : roleSeq;
  }
  public Integer getLineId() {
    return lineId == null ? 0 : lineId;
  }

  public void setLineId(Integer lineId) {
    this.lineId = lineId;
  }

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

  public static TrecordDto from(TrecordEntity e) {
    if (e == null) return null;
    return TrecordDto.builder()
        .recordSeq(e.getRecordSeq())
        .callStartDateTime(e.getCallStartDateTime() != null
            ? e.getCallStartDateTime().toLocalDateTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            : null)
        .callEndDateTime(e.getCallEndDateTime() != null
            ? e.getCallEndDateTime().toLocalDateTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            : null)
        .audioPlayTime(e.getAudioPlayTime() != null ? e.getAudioPlayTime().toString() : null)
        .ioDiscdVal(e.getIoDiscdVal())
        .number1(e.getNumber1())
        .number2(e.getNumber2())
        .audioFileDir(e.getAudioFileDir())
        .callStatus(e.getCallStatus())
        .regDate(e.getRegDate() != null
            ? e.getRegDate().toLocalDateTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            : null)
        .lineId(e.getLineId())
        .ownerMemberSeq(e.getOwnerMemberSeq())
        .branchSeq(e.getBranchSeq())
        .build();
  }
}
