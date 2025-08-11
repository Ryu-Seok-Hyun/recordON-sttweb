// src/main/java/com/sttweb/sttweb/controller/SttSearchController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.service.SttSearchService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stt")
@RequiredArgsConstructor
public class SttSearchController {

  private final SttSearchService svc;

  /** q=text 검색 → filename 유니크만 반환 */
  @GetMapping({"/search", "/search/filenames"})
  public ResponseEntity<Map<String, Object>> searchFilenames(
      @RequestParam(name="q", required=false, defaultValue="") String q,
      @RequestParam(name="page", defaultValue="0") int page,
      @RequestParam(name="size", defaultValue="10") int size
  ) {
    return ResponseEntity.ok(svc.searchFilenames(q, page, size));
  }

  // 원본 문서 조회
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable String id) {
    return ResponseEntity.ok(svc.getById(id));
  }
}
