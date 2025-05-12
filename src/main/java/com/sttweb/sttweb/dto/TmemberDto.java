package com.sttweb.sttweb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sttweb.sttweb.entity.TmemberEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class TmemberDto {

  /**
   * 회원정보 조회/응답용 DTO
   */
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

    /**
     * Java 코드 상에서는 CamelCase 로 쓰고,
     * JSON 직렬화/역직렬화 시에는 "role_seq" 로 매핑
     */
    @JsonProperty("role_seq")
    private Integer roleSeq;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String token;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tokenType;

    /**
     * Entity → DTO 변환 헬퍼
     */
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
          .roleSeq(e.getRoleSeq())    // Lombok Builder 에서 .roleSeq(...) 메서드 사용
          .build();
    }
  }

  /**
   * 회원가입 요청용 DTO
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SignupRequest {
    private String userId;
    private String userPass;
    private Integer branchSeq;
    private Integer employeeId;
    private String number;

    /** 선택: "0"=관리자, 그 외=일반 */
    private String userLevel;

    /** 선택: 1~3 사이의 역할 번호 */
    private Integer roleSeq;
  }

  /**
   * 로그인 요청용 DTO
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LoginRequest {
    private String userId;
    private String userPass;
  }

  /**
   * 비밀번호 변경 요청용 DTO
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PasswordChangeRequest {
    private String oldPassword;
    private String newPassword;
  }

  /**
   * 활성/비활성 상태 변경 요청용 DTO
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StatusChangeRequest {
    private boolean active;
  }

}
