
package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordSearchResponseDto {
  /** 전체 건수 */
  private long totalCount;
  /** 수신(IN) 건수 */
  private long inboundCount;
  /** 발신(OUT) 건수 */
  private long outboundCount;
  /** 실제 페이지 데이터 */
  private List<TrecordDto> items;
}
