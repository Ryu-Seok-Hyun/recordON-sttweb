package com.sttweb.sttweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Setter
@Getter
@DynamicInsert
@Table(name = "tbranch")
public class TbranchEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "branch_seq")
  private Integer branchSeq;

  @Column(name = "companyid", nullable = false)
  private Integer companyId;

  @Column(name = "phone", length = 15)
  private String phone;

  @Column(name = "companyname", length = 30)
  private String companyName;

  @Column(name = "ip_type")
  private Integer ipType;

  @Column(name = "pb_ip", length = 20)
  private String pbIp;

  @Column(name = "pb_port", length = 10)
  private String pbPort;

  @Column(name = "p_ip", length = 20)
  private String pIp;

  @Column(name = "p_port", length = 10)
  private String pPort;

  @Column(name = "hq_yn", length = 1)
  private String hqYn;

  @Column(name = "discd", columnDefinition = "INT default 0")
  private Integer discd;

  /** 생성 시각 (자동 채워짐) */
  @CreationTimestamp                                    // ← 추가
  @Column(name = "crtime", updatable = false)           // 변경: columnDefinition 대신 updatable=false
  private LocalDateTime crtime;

//  @Column(name = "db_type")
//  private Integer dbType;
//
//  @Column(name = "db_ip", length = 20)
//  private String dbIp;
//
//  @Column(name = "db_port", length = 10)
//  private String dbPort;
//
//  @Column(name = "db_name", length = 20)
//  private String dbName;
//
//  @Column(name = "db_user", length = 30)
//  private String dbUser;
//
//  @Column(name = "db_pass", length = 30)
//  private String dbPass;
//
//  @Column(name = "db_flag", length = 1)
//  private String dbFlag;
//
//  @Column(name = "db_discd")
//  private Integer dbDiscd;
//
//  @Column(name = "mail_discd")
//  private Integer mailDiscd;
//
//  @Column(name = "mail_manager", length = 20)
//  private String mailManager;
//
//  @Column(name = "mail_address", length = 30)
//  private String mailAddress;
}
