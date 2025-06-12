package com.sttweb.sttweb.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tline")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tline {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "line_id")
  private Integer lineId;

  @Column(name = "call_num")
  private String callNum;

  @Column(name = "line_name")
  private String lineName;

  // 필요에 따라 추가 필드들
  // @Column(name = "branch_seq")
  // private Integer branchSeq;

  // @Column(name = "is_active")
  // private Boolean isActive;
}