package com.sttweb.sttweb.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tmember_role")
@Getter
@Setter
@NoArgsConstructor
public class TmemberRoleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "role_seq")
  private Integer roleSeq;

  @Column(name = "role_code", length = 20, nullable = false)
  private String roleCode;

  @Column(name = "description", length = 50, nullable = false)
  private String description;
}