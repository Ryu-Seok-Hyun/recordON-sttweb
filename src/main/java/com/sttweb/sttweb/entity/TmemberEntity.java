package com.sttweb.sttweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Setter
@Table(name = "tmember")
@EntityListeners(AuditingEntityListener.class) //  자동으로 생성일, 수정일" 같은 값을 넣어주는 기능을 활성화하는 어노테이션
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

  @Column(name = "user_pass", nullable = false, length = 30)
  private String userPass;

  @Column(name = "admin_yn", length = 1)
  private String adminYn;

  @Column(name = "number", nullable = false, length = 15)
  private String number;

  @Column(name = "discd")
  private Integer discd;

  @Column(name = "crtime", length = 19)
  private String crtime;

  @Column(name = "reguser_id", length = 30)
  private String reguserId;


}
