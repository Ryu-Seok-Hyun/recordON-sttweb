package com.sttweb.sttweb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sttweb.sttweb.entity.TmemberEntity;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.*;
import com.sttweb.sttweb.dto.GrantDto;

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
    private String  branchName;
    private Integer employeeId;
    private String  userId;
    private String  userLevel;
    private String  number;
    private Integer discd;
    private String  crtime;
    private String  udtime;
    private String  reguserId;
    private Integer maskFlag;

    /** 직책(포지션) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String position;

    /** 직급 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String rank;

    /** 부서 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String department;

    /** 초기화된 비밀번호 사용 중인지 표시 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean mustChangePassword;

    /** JSON에는 role_seq로 직렬화 */
    @JsonProperty("role_seq")
    private Integer roleSeq;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String token;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tokenType;

    private Integer currentBranchSeq;
    private String  currentBranchName;

    @JsonProperty("hq_yn")
    private Boolean hqYn;


    /**
     * Entity → DTO 변환 헬퍼 (branchName은 서비스/컨트롤러에서 채워 줌)
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
          .roleSeq(e.getRoleSeq())
          .position(e.getPosition())
          .rank(e.getRank())
          .department(e.getDepartment())
          .maskFlag(e.getMaskFlag())
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
    @NotBlank(message = "userId를 입력하세요.")
    private String userId;

    @NotBlank(message = "password를 입력하세요.")
    @Pattern(
        regexp  = "^(?=.*[a-z])(?=.*\\d)(?=.*\\W).{8,}$",
        message = "비밀번호는 최소 8자 이상이며, 영어 소문자·숫자·특수문자를 포함해야 합니다."
    )
    private String userPass;

    @NotNull(message = "branchSeq를 입력하세요.")
    private Integer branchSeq;

    private Integer employeeId;

    @NotBlank(message = "number를 입력하세요.")
    private String number;

    @NotBlank(message = "userLevel을 입력하세요.")
    /** 필수: "0"=본사 관리자, "1"=지사 관리자, "2"=지사 일반 유저 */
    private String userLevel;

    /** 선택: 1~4 사이의 역할 번호 */
    private Integer roleSeq;

    /** 선택: 회원가입 시 함께 부여할 권한 목록 */
    private List<GrantDto> grants;

    /** 선택: 직책(포지션) */
    private String position;

    /** 선택: 직급 */
    private String rank;

    /** 선택: 부서 */
    private String department;

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
    private String targetDomain;
    private Integer branchSeq;
  }

  @Data
  @AllArgsConstructor
  public static class LoginResponse {
    private String token;
    @JsonProperty("hq_yn")
    private boolean hqUser;
    private String redirectUrl;
    private String message;
    private Integer currentBranchSeq;
    private String  currentBranchName;
    private Integer branchSeq;
    private String  branchName;
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

  /**
   * 업데이트 요청용 DTO
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UpdateRequest {
    @NotBlank(message = "내선번호(number)는 필수입니다.")
    private String number;
    private Integer branchSeq;
    private Integer employeeId;
    private Integer roleSeq;
    private String  userLevel;
    private Boolean active;
    private String oldPassword;
    private String newPassword;
    private String position;
    private String rank;
    private String department;
  }
}
