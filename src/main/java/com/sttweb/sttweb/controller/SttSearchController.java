// src/main/java/com/sttweb/sttweb/controller/SttSearchController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.SttSearchDtos.Page;
import com.sttweb.sttweb.service.SttSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stt")
@RequiredArgsConstructor
public class SttSearchController {

  private final SttSearchService svc;


  // SttSearchController.java
// SttSearchController.java (현재 그대로 OK, q는 optional)
  @GetMapping("/search")
  public ResponseEntity<Page> search(
      @RequestParam(name="q", required=false, defaultValue="") String q,
      @RequestParam(name="page", defaultValue="0") int page,
      @RequestParam(name="size", defaultValue="10") int size
  ) {
    return ResponseEntity.ok(svc.search(q, page, size));
  }




  // 원본 문서 조회 (필드 전체 확인용)
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable String id) {
    return ResponseEntity.ok(svc.getById(id));
  }
}
