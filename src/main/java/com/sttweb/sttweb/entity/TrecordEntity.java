// src/main/java/com/sttweb/sttweb/entity/TrecordEntity.java
package com.sttweb.sttweb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Where;
import java.sql.Time;
import java.sql.Timestamp;

@Getter
@Setter
@Entity
@Where(clause = "audioPlayTime <> '00:00:00'")
@Table(
    name = "trecord",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_call_start_number",
        columnNames = {"callStartDateTime", "number1"}
    )
)
public class TrecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "record_seq")
  private Integer recordSeq;


  @Column(name = "callStartDateTime", columnDefinition = "DATETIME")
  private Timestamp callStartDateTime;

  @Column(name = "callEndDateTime", columnDefinition = "DATETIME")
  private Timestamp callEndDateTime;

  @Column(name = "audioPlayTime", columnDefinition = "TIME")
  private Time audioPlayTime;


  @Column(name = "IO_discd_val", length = 2)
  private String ioDiscdVal;

  @Column(name = "number1", length = 50)
  private String number1;

  @Column(name = "number2", length = 50)
  private String number2;

  @Column(name = "audioFileDir", length = 70)
  private String audioFileDir;

  @Column(name = "call_status", length = 10)
  private String callStatus;

  @Column(name = "reg_date", columnDefinition = "DATETIME")
  private Timestamp regDate;


  /**
   * 이 녹취를 생성한 사용자(memberSeq)
   */
  @Column(name = "ownerMemberSeq")
  private Integer ownerMemberSeq;

  private Integer lineId;
  /**
   * 이 녹취가 발생한 지점(branchSeq)
   */
  @Column(name = "branch_seq")
  private Integer branchSeq;
}