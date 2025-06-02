// src/main/java/com/sttweb/sttweb/dto/IniNameExtDto.java
package com.sttweb.sttweb.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;


@Entity
@Table(
    name = "trecord_tel_list",
    uniqueConstraints = @UniqueConstraint(name = "uk_record_callnum", columnNames = "call_num")
)
/**
 * INI 파일 파싱 시에만 사용하는 DTO
 *   - callNum  : 녹취 내선번호
 *   - userName : 사용자명
 *
 * ID나 critime 필드는 파싱 단계에서는 알 수 없으므로 포함하지 않습니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IniNameExtDto {


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
   *  • @CreationTimestamp: INSERT 시점에 Hibernate가 자동으로 현재 시각을 채워줌
   *  • updatable = false: 이후 UPDATE 시에도 절대 바뀌지 않도록 함
   */
  @CreationTimestamp
  @Column(name = "critime", nullable = false, updatable = false)
  private LocalDateTime critime;
}