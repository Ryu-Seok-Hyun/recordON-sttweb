package com.sttweb.sttweb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


// 등록된 회원이 각각의 회선들에 대해 어떤 권한을 갖고 있는지

@Entity
@Table(
    name = "tmember_line_perm",
    uniqueConstraints = @UniqueConstraint(name = "uk_member_line", columnNames = { "member_seq", "line_id" })
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TmemberLinePermEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 어떤 회원(member_seq)인지
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_seq", nullable = false)
  private TmemberEntity member;

  /**
   * 어떤 회선(line_id)인지
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "line_id", nullable = false)
  private TrecordTelListEntity line;

  /**
   * 해당 회원-회선 조합에 부여된 권한 (tmember_role.role_seq)
   */
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "role_seq", nullable = false)
  private TmemberRoleEntity role;

  /**
   * 매핑이 생성된 시각 (자동 채워짐)
   */
  @CreationTimestamp
  @Column(name = "regtime", nullable = false, updatable = false)
  private LocalDateTime regtime;
}
