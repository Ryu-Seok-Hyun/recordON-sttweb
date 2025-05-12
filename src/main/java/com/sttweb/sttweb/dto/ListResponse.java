package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

/**
 * 페이징된 결과 + 메타데이터 응답용 DTO
 */
@Data
@AllArgsConstructor
public class ListResponse<T> {
  /** 전체 레코드 개수 */
  private long count;
  /** 요청한 페이지의 데이터 리스트 */
  private List<T> items;
  /** 현재 페이지 번호 (0부터 시작) */
  private int page;
  /** 한 페이지당 데이터 개수 */
  private int size;
  /** 전체 페이지 수 */
  private int totalPages;
}
