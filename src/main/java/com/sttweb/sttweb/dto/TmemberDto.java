package com.sttweb.sttweb.dto;

import com.sttweb.sttweb.entity.TmemberEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class TmemberDto {

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class Info {
    private Integer memberSeq;   // 회원 일련번호(PK)
    private Integer branchSeq;   // 지점 일련번호(FK)
    private Integer employeeId;  // 사원 아이디
    private String userId;       // 사용자 아이디
    private String userLevel;      // 관리자여부(0/1) 관리자는 0 그외 1
    private String number;       // 내선번호
    private Integer discd;       // 사용 여부 , 0은 활성 1은 비활성
    private String crtime;       // 생성시간(YYYY-MM-DD HH:mm:ss)
    private String udtime;   // 업데이트 시간(YYYY-MM-DD HH:mm:ss)
    private String reguserId;    // 등록자 아이디

    private String token;
    private String tokenType = "Bearer";

    public static Info fromEntity(TmemberEntity e) {
      return Info.builder()
          .memberSeq(e.getMemberSeq())
          .branchSeq(e.getBranchSeq())
          .employeeId(e.getEmployeeId())
          .userId(e.getUserId())
          .userLevel(e.getUserLevel())
          .number(e.getNumber())
          .discd(e.getDiscd())
          .crtime(e.getCrtime())
          .udtime(e.getUdtime())
          .reguserId(e.getReguserId())
          .build();
    }

    public TmemberEntity toEntity() {
      TmemberEntity e = new TmemberEntity();
      e.setMemberSeq(this.memberSeq);
      e.setBranchSeq(this.branchSeq);
      e.setEmployeeId(this.employeeId);
      e.setUserId(this.userId);
      e.setUserLevel(this.userLevel);
      e.setNumber(this.number);
      e.setDiscd(this.discd);
      e.setCrtime(this.crtime);
      e.setUdtime(this.udtime);
      e.setReguserId(this.reguserId);
      return e;
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SignupRequest {
    private String userId;
    private String userPass;
    private Integer branchSeq;
    private Integer employeeId;
    private String number;
    private String userLevel;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LoginRequest {
    private String userId;
    private String userPass;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PasswordChangeRequest {
    private String oldPassword;
    private String newPassword;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StatusChangeRequest {
    private boolean active;
  }
}
