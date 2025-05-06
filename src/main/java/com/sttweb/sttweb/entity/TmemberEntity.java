package com.sttweb.sttweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
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

  @Column(name = "employeeid", nullable = true)
  private Integer employeeId;

  @Column(name = "user_id", nullable = false, length = 30)
  private String userId;

  @Column(name = "user_pass", nullable = false, length = 30)
  private String userPass;

  @Column(
      name = "user_level",
      nullable = false,
      length = 1,
      columnDefinition = "CHAR(1) DEFAULT '1'"
  )
  private String userLevel = "1";

  @Column(name = "number", nullable = false, length = 15)
  private String number;

  @Column(
      name = "discd",
      nullable = false,
      columnDefinition = "INT DEFAULT 0"
  )
  private Integer discd = 0;

  @Column(name = "crtime", length = 19)
  private String crtime;

  @Column(name = "udtime", length = 19)
  private String udtime;

  @Column(name = "reguser_id", length = 30)
  private String reguserId;


}
