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
    private Integer memberSeq;
    private Integer branchSeq;
    private Integer employeeId;
    private String userId;
    private String userLevel;
    private String number;
    private Integer discd;
    private String crtime;
    private String udtime;
    private String reguserId;
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

    /** 선택: "0"=관리자, 아니면 일반 */
    private String userLevel;

    /** 선택: 1~3 */
    private Integer roleSeq;
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
