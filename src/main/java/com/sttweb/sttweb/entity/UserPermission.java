package com.sttweb.sttweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "t_user_permission")
@Getter
@Setter
public class UserPermission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "permission_id")
  private Long permissionId;

  @Column(name = "grantee_seq", nullable = false)
  private Integer granteeSeq;

  @Column(name = "target_seq", nullable = false)
  private Integer targetSeq;

  @Column(name = "perm_level", nullable = false)
  private Integer permLevel;  // 1=READ, 2=LISTEN, 3=DOWNLOAD
}
