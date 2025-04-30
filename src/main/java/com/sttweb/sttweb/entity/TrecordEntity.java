package com.sttweb.sttweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Id;

@Getter
@Setter
@Entity
@Table(name = "trecord", uniqueConstraints = {
    @UniqueConstraint(name = "uk_call_start_number", columnNames = {"callStartDateTime", "number1"})
})
public class TrecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "record_seq")
  private Integer recordSeq;

  @Column(name = "callStartDateTime", length = 19)
  private String callStartDateTime;

  @Column(name = "callEndDateTime", length = 19)
  private String callEndDateTime;

  @Column(name = "audioPlayTime", length = 8)
  private String audioPlayTime;

  @Column(name = "IO_discd_val", length = 2)
  private String ioDiscdVal;

  @Column(name = "number1", length = 4)
  private String number1;

  @Column(name = "number2", length = 15)
  private String number2;

  @Column(name = "audioFileDir", length = 70)
  private String audioFileDir;

  @Column(name = "call_status", length = 10)
  private String callStatus;

  @Column(name = "reg_date", length = 19)
  private String regDate;
}
