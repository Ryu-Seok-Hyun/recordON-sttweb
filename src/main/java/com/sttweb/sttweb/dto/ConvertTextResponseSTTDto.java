package com.sttweb.sttweb.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConvertTextResponseSTTDto {
  /** 변환 대상 녹취 파일 ID */
  private Long recordId;
  /** STT 결과 텍스트 */
  private String text;
}


