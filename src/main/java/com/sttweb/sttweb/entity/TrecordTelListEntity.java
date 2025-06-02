// src/main/java/com/sttweb/sttweb/entity/TrecordTelListEntity.java
package com.sttweb.sttweb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "trecord_tel_list",
    uniqueConstraints = @UniqueConstraint(name = "uk_record_callnum", columnNames = "call_num")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrecordTelListEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Integer id;

  /** 녹취 내선번호 */
  @Column(name = "call_num", length = 15, nullable = false)
  private String callNum;

  /** 사용자명 */
  @Column(name = "user_name", length = 50)
  private String userName;

  /**
   * 생성 시각
   *  • INSERT 시점에 Hibernate가 자동으로 현재 시각을 채워준다 (@CreationTimestamp)
   *  • updatable = false 로 지정하면, 이후 UPDATE 때 값이 변경되지 않는다
   */
  @CreationTimestamp
  @Column(name = "critime", nullable = false, updatable = false)
  private LocalDateTime critime;
}