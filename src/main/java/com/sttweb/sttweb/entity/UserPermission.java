package com.sttweb.sttweb.entity;

import jakarta.persistence.*;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "tuser_permission")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPermission {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "permission_id")
  private Long permissionId;

  @Column(name = "member_seq", nullable = false)
  private Integer memberSeq;

  @Column(name = "line_id", nullable = false)
  private Integer lineId;

  @Column(name = "perm_level", nullable = false)
  private Integer permLevel;

  @Column(name = "crtime")
  @CreationTimestamp
  private LocalDateTime crtime;


  public Integer getLineId() { return lineId; } // DB column 그대로

  // line_id로 tmember.number(내선) 찾아오는 유틸
  public String fetchLineNumber(Map<Integer, String> lineIdToNumberMap) {
    if (this.lineId == null) return null;
    return lineIdToNumberMap.get(this.lineId);
  }
}
