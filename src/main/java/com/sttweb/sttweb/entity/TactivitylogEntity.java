package com.sttweb.sttweb.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity mapping for activity logs.
 */
@Entity
@Table(name = "tactivitylog")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TactivitylogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "activity_seq")
  private Integer activitySeq;

  @Column(name = "type", length = 10)
  private String type;

  @Column(name = "branch_seq")
  private Integer branchSeq;

  @Column(name = "companyname", length = 30)
  private String companyName;

  @Column(name = "member_seq")
  private Integer memberSeq;

  @Lob
  @Column(name = "user_id", columnDefinition = "TEXT")
  private String userId;

  @Column(name = "activity", nullable = false, length = 20)
  private String activity;

  @Lob
  @Column(name = "contents", length = 100)
  private String contents;

  @Column(name = "dir", length = 70)
  private String dir;

  @Column(name = "employeeid")
  private Integer employeeId;

  @Column(name = "pb_ip", length = 20)
  private String pbIp;

  @Column(name = "pv_ip", length = 20)
  private String pvIp;


  @Column(name = "crtime", nullable = false, length = 19)
  private String crtime;

  @Column(name = "worker_seq", nullable = false)
  private Integer workerSeq;

  @Column(name = "worker_id", nullable = false, length = 30)
  private String workerId;

}