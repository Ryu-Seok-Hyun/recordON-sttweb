package com.sttweb.sttweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sttweb.sttweb.entity.TbranchEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TbranchDto {

  private Integer branchSeq; // 지점 일련번호(PK)
  private Integer companyId; // 회사 일련번호(FP)
  private String phone; //지점 전화번호
  private String companyName; // 회사명
  private Integer ipType; // IP타입 (예: 0=내부, 1=외부)

  @JsonProperty("pIp")//IP 주소
  private String pIp;

  @JsonProperty("pPort")
  private String pPort;//P포트

  private String pbIp; // IP 주소
  private String pbPort; // PB 포트
  private String hqYn; // 본사 여부
  private Integer discd; // 삭제 여부

  private String crtime; //생성 시간

  private Boolean isAlive;
  private LocalDateTime lastHealthCheck;
  private LocalDateTime lastDowntime;

//  private Integer dbType; // DB 타입
//  private String dbIp; // DB IP 주소
//  private String dbPort; // DB 포트
//  private String dbName; //DB 이름
//  private String dbUser; //DB 사용자명
//  private String dbPass; // DB 비밀번호
//  private String dbFlag; // DB 플래그 (활성/비활성)
//  private Integer dbDiscd; // DB 삭제 여부
//  private Integer mailDiscd; // 메일 전송 여부
//  private String mailManager; // 메일 관리자ID
//  private String mailAddress; // 메일주소


  public static TbranchDto fromEntity(TbranchEntity e) {
    return TbranchDto.builder()
        .branchSeq(e.getBranchSeq())
        .companyId(e.getCompanyId())
        .phone(e.getPhone())
        .companyName(e.getCompanyName())
        .ipType(e.getIpType())
        .pbIp(e.getPbIp())
        .pbPort(e.getPbPort())
        .pIp(e.getPIp())
        .pPort(e.getPPort())
        .hqYn(e.getHqYn())
        .discd(e.getDiscd())
        .crtime(e.getCrtime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        .isAlive(e.getIsAlive())
        .lastHealthCheck(e.getLastHealthCheck())
        .lastDowntime(e.getLastDowntime())
        .build();
  }
}

