// src/main/java/com/sttweb/sttweb/controller/RecordTelController.java
package com.sttweb.sttweb.controller;


import com.sttweb.sttweb.dto.IniNameExtDto;
import com.sttweb.sttweb.service.IniFileService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class RecordTelController {

  private final IniFileService service;

  // ──────────────────────────────────────────────────────────────────
  // 1) INI 파싱 전용
  // ──────────────────────────────────────────────────────────────────

  @GetMapping("/api/ini/list")
  public ResponseEntity<List<IniNameExtDto>> listIniData() {
    try {
      List<IniNameExtDto> list = service.parseIniNameExt();
      return ResponseEntity.ok(list);
    } catch (IOException e) {
      return ResponseEntity.status(500).build();
    }
  }

  /**
   * POST /api/ini/sync
   *  → 파싱 후 DB에 신규 INSERT(중복 스킵)
   *  → 삽입된 레코드 각각의 DTO(+critime)를 JSON 배열로 반환
   *
   *  예시 응답:
   *  [
   *    { "id": 10, "callNum": "202", "userName": "서인석", "critime": "2025-06-02T21:40:12.123" },
   *    { "id": 11, "callNum": "203", "userName": "이홍규", "critime": "2025-06-02T21:40:12.456" }
   *  ]
   */
  @PostMapping("/api/ini/sync")
  public ResponseEntity<?> syncIniToDb() {
    try {
      List<IniNameExtDto> insertedList = service.syncFromIni();
      return ResponseEntity.ok(insertedList);
    } catch (IOException e) {
      // 에러가 난 경우, 에러 메시지만 반환
      Map<String, Object> resp = new HashMap<>();
      resp.put("error", "INI 파일 읽기/파싱 중 오류 발생: " + e.getMessage());
      return ResponseEntity.status(500).body(resp);
    }
  }

  // ──────────────────────────────────────────────────────────────────
  // 2) DB 조회 전용
  // ──────────────────────────────────────────────────────────────────

  @GetMapping("/api/trecord/list")
  public ResponseEntity<List<IniNameExtDto>> getAllList() {
    List<IniNameExtDto> list = service.getAll();
    return ResponseEntity.ok(list);
  }

  @GetMapping("/api/trecord/{callNum}")
  public ResponseEntity<IniNameExtDto> getByCallNum(@PathVariable String callNum) {
    IniNameExtDto dto = service.getByCallNum(callNum);
    if (dto == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(dto);
  }
}
