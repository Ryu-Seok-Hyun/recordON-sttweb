package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class TlineDto {

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Info {
    private Integer lineId;
    private String callNum;
    private String lineName;
  }
}
