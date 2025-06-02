// src/main/java/com/sttweb/sttweb/controller/RecordTelController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.IniNameExtDto;
import com.sttweb.sttweb.service.RecordTelService;
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

  private final RecordTelService service;

  // ──────────────────────────────────────────────────────────────────
  // 1) GET /api/ini/list 으로도 동기화 + 결과 반환
  // ──────────────────────────────────────────────────────────────────

  /**
   * GET  /api/ini/list
   *  → INI 파일을 파싱 → DB 동기화 →
   *    DB에 저장된 전체 레코드(id, callNum, userName, critime) 반환
   *
   * ※ 기존에 POST로만 되어 있던 부분을 GET으로 바꿨습니다.
   */
  @GetMapping("/api/ini/list")
  public ResponseEntity<List<IniNameExtDto>> parseAndSyncAll() {
    try {
      // 1) 파일 파싱 → 2) 중복 검사 후 DB에 신규 INSERT →
      // 3) DB에 저장된 전체 목록 조회하는 service.getAll()
      //    ("파일 파싱 결과만" 반환하는 것이 아니라, "DB에 실제 저장된 값"을 반환)
      service.syncFromIni();        // 신규 항목만 INSERT
      List<IniNameExtDto> all = service.getAll();
      return ResponseEntity.ok(all);

    } catch (IOException e) {
      return ResponseEntity.status(500).build();
    }
  }

  /**
   * POST  /api/ini/sync
   *  → INI 파일 파싱 후 DB 동기화(신규 INSERT) → 삽입된 DTO 리스트 반환
   */
  @PostMapping("/api/ini/sync")
  public ResponseEntity<Map<String, Object>> syncIniToDb() {
    Map<String, Object> resp = new HashMap<>();

    try {
      // 서비스가 List<RecordTelDto>를 반환하도록 짜여 있다면 .size()로 건수를 구할 수 있습니다.
      List<IniNameExtDto> insertedList = service.syncFromIni();
      int insertedCount = insertedList.size();

      resp.put("inserted", insertedCount);
      resp.put("message", "새로운 계정 " + insertedCount + "건이 추가되었습니다.");

      return ResponseEntity.ok(resp);

    } catch (IOException e) {
      resp.put("inserted", 0);
      resp.put("message", "INI 파일 읽기/파싱 중 오류 발생: " + e.getMessage());
      return ResponseEntity.status(500).body(resp);
    }
  }

  // ──────────────────────────────────────────────────────────────────
  // 2) DB 조회 전용 (이미 저장된 모든 레코드 → JSON)
  // ──────────────────────────────────────────────────────────────────

  /**
   * GET /api/trecord/list
   *  → trecord_tel_list 테이블의 모든 레코드를 반환 (id, callNum, userName, critime 포함)
   */
  @GetMapping("/api/trecord/list")
  public ResponseEntity<List<IniNameExtDto>> getAllList() {
    List<IniNameExtDto> all = service.getAll();
    return ResponseEntity.ok(all);
  }

  /**
   * GET /api/trecord/{callNum}
   *  → callNum(내선번호)로 단일 조회 후 반환 (없으면 404)
   */
  @GetMapping("/api/trecord/{callNum}")
  public ResponseEntity<IniNameExtDto> getByCallNum(@PathVariable String callNum) {
    IniNameExtDto dto = service.getByCallNum(callNum);
    if (dto == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(dto);
  }
}
