package com.sttweb.sttweb.entity;

import jakarta.persistence.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "tmember")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor                // ← public no-arg 생성자
@AllArgsConstructor
@Builder
public class TmemberEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "member_seq")
  private Integer memberSeq;

  @Column(name = "branch_seq")
  private Integer branchSeq;

  @Column(name = "employeeid")
  private Integer employeeId;

  @Column(name = "user_id", nullable = false, length = 30)
  private String userId;

  @Column(name = "user_pass", nullable = false, length = 100)
  private String userPass;

  /**
   * "0" = 관리자, "1" = 일반
   * 필드 기본값과 DDL DEFAULT 까지 설정
   */
  @Builder.Default
  @Column(
      name = "user_level",
      nullable = false,
      length = 1,
      columnDefinition = "CHAR(1) NOT NULL DEFAULT '1'"
  )
  private String userLevel = "1";

  @Column(name = "number", nullable = false, length = 15)
  private String number;

  @Column(name = "position", length = 50, nullable = true)
  private String position;

  @Column(name = "rank", length = 50, nullable = true)
  private String rank;

  @Column(name = "department", length = 50, nullable = true)
  private String department;

  @Builder.Default
  @Column(
      name = "discd",
      nullable = false,
      columnDefinition = "INT NOT NULL DEFAULT 0"
  )
  private Integer discd = 0;

  @Column(name = "crtime", length = 19)
  private String crtime;

  @Column(name = "udtime", length = 19)
  private String udtime;

  @Column(name = "reguser_id", length = 30)
  private String reguserId;

  /**
   * 1=조회만, 2=조회+청취, 3=조회+청취+다운로드
   */
  @Builder.Default
  @Column(
      name = "role_seq",
      nullable = false,
      columnDefinition = "INT NOT NULL DEFAULT 1"
  )
  private Integer roleSeq = 1;


  /** 마스킹 여부 (0=마스킹, 1=마스킹 안 함) */
  @Builder.Default
  @Column(name = "mask_flag", nullable = false, columnDefinition = "TINYINT(1) default 0")
  private Integer maskFlag = 0;


  /**
   * ▶ 회원 한 명이 여러 회선에 권한을 가질 수 있으므로 OneToMany 매핑
   */
  @OneToMany(
      mappedBy = "member",
      cascade = CascadeType.ALL,
      orphanRemoval = true
  )
  private List<TmemberLinePermEntity> linePerms;
}