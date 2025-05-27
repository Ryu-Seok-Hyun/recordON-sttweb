package com.sttweb.sttweb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "tuser_permission",
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

  /** 추가된 생성 시각 컬럼 */
  @Column(name = "crtime", nullable = false, updatable = false,
      columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
  private LocalDateTime crtime;

}
