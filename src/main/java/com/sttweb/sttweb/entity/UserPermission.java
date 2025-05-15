package com.sttweb.sttweb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "t_user_permission",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_grantee_target",
        columnNames = {"grantee_user_id", "target_user_id"}
    )
)
@Getter
@Setter
public class UserPermission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "permission_id")
  private Long permissionId;

  @Column(name = "grantee_user_id", length = 30, nullable = false)
  private String granteeUserId;

  @Column(name = "target_user_id", length = 30, nullable = false)
  private String targetUserId;

  @Column(name = "perm_level", nullable = false)
  private Integer permLevel;
}
