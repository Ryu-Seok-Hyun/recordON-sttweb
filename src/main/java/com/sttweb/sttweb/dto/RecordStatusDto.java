package com.sttweb.sttweb.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
// 레코드온 데이터 잘 생기는지 체크 유무
public class RecordStatusDto {
  private String fileName;     // e.g. "0203-O-028377111_20250619125335.wav"
  private boolean jsonExists;  // json 생성 여부

  public RecordStatusDto(String fileName, boolean jsonExists) {
    this.fileName = fileName;
    this.jsonExists = jsonExists;
  }

}
