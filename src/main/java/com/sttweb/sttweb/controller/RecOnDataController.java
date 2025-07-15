package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.RecordStatusDto;
import com.sttweb.sttweb.service.RecOnDataService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recon_data")
public class RecOnDataController {

  private final RecOnDataService recService;

  public RecOnDataController(RecOnDataService recService) {
    this.recService = recService;
  }

  /**
   * 해당 날짜 폴더 내 .wav 파일 리스트와 JSON 생성 여부 반환
   * GET /api/rec-on-data/{date}/status
   */
  @GetMapping("/{date}/status")
  public ResponseEntity<List<RecordStatusDto>> getStatusByDate(
      @PathVariable("date") String date) {
    List<RecordStatusDto> list = recService.listRecordStatus(date);
    return ResponseEntity.ok(list);
  }

  /**
   * 특정 파일 하나만 JSON 생성 여부 반환
   * GET /api/rec-on-data/{date}/status/{fileName}
   */
  @GetMapping("/{date}/status/{fileName:.+}")
  public ResponseEntity<Map<String, Boolean>> getSingleStatus(
      @PathVariable("date") String date,
      @PathVariable("fileName") String fileName) {
    boolean exists = recService.isJsonGenerated(date, fileName);
    return ResponseEntity.ok(Map.of("jsonExists", exists));
  }
}