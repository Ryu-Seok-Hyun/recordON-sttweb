// src/main/java/com/sttweb/sttweb/controller/SttSearchController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.service.SttSearchService;
import com.sttweb.sttweb.service.TrecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/stt")
@RequiredArgsConstructor
public class SttSearchController {

  private final SttSearchService sttSearchService;
  private final TrecordService trecordService;

  // ES filenames → DB 조인 결과 반환 (한 번에)
  @GetMapping("/search/join")
  public ResponseEntity<Map<String, Object>> joinRecordsByQueries(
      @RequestParam(name = "s") List<String> queries,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "fpage", defaultValue = "0") int fpage,
      @RequestParam(name = "fsize", defaultValue = "1000") int fsize
  ) {
    // 1) ES: filename 유니크
    Map<String, Object> fnPage = sttSearchService.searchFilenamesArray(queries, fpage, fsize);
    @SuppressWarnings("unchecked")
    List<String> esNames = (List<String>) fnPage.getOrDefault("filenames", Collections.emptyList());

    // 2) DB 조인용: 'basename.wav' (소문자) 변환
    List<String> basenames = esNames.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.toLowerCase(Locale.ROOT).endsWith(".wav")
            ? s.toLowerCase(Locale.ROOT)
            : (s.toLowerCase(Locale.ROOT) + ".wav"))
        .distinct()
        .toList();

    // 3) DB 조회 + 최신순
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "callStartDateTime"));
    Page<?> recordPage = trecordService.searchByAudioFileBasenames(basenames, pageable);

    // 4) 페이징 JSON + filenames 포함
    Map<String, Object> body = buildPageResponse(recordPage);
    body.put("filenames", esNames);
    return ResponseEntity.ok(body);
  }

  /**
   * 메인 검색:
   * - ES에서 filename 추출
   * - DB에서 해당 basenames로 페이징 조회
   */
  @GetMapping("/search")
  public ResponseEntity<Map<String, Object>> search(
      @RequestParam(name = "s", required = false, defaultValue = "") String s,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "fpage", defaultValue = "0") int fpage,
      @RequestParam(name = "fsize", defaultValue = "10") int fsize
  ) {
    Map<String, Object> fnPage = sttSearchService.searchFilenames(s, fpage, fsize);
    @SuppressWarnings("unchecked")
    List<String> filenames = (List<String>) fnPage.getOrDefault("filenames", Collections.emptyList());

    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "callStartDateTime"));
    Page<?> recordPage = trecordService.searchByAudioFileBasenames(filenames, pageable);

    Map<String, Object> body = buildPageResponse(recordPage);
    body.put("filenames", filenames);
    return ResponseEntity.ok(body);
  }

  /** s=text 검색 → filename 유니크만 페이징 반환 */
  @GetMapping("/search/filenames")
  public ResponseEntity<Map<String, Object>> searchFilenames(
      @RequestParam(name = "s", required = false, defaultValue = "") String s,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    return ResponseEntity.ok(sttSearchService.searchFilenames(s, page, size));
  }

  /** s 배열(OR) 검색 → filename 유니크만 페이징 반환 */
  @GetMapping("/search/filenames/array")
  public ResponseEntity<Map<String, Object>> searchFilenamesArray(
      @RequestParam(name = "s") List<String> queries,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    return ResponseEntity.ok(sttSearchService.searchFilenamesArray(queries, page, size));
  }

  /** 원본 문서 조회 */
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable String id) {
    return ResponseEntity.ok(sttSearchService.getById(id));
  }

  /** Page<?> → 기존 JSON 구조 직렬화 */
  private Map<String, Object> buildPageResponse(Page<?> page) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("content", page.getContent());
    m.put("totalElements", page.getTotalElements());
    m.put("totalPages", page.getTotalPages());
    m.put("size", page.getSize());
    m.put("number", page.getNumber());
    m.put("numberOfElements", page.getNumberOfElements());
    m.put("empty", page.isEmpty());
    m.put("first", page.isFirst());
    m.put("last", page.isLast());

    Pageable p = page.getPageable();
    Sort s = page.getSort();

    m.put("pageable", Map.of(
        "pageNumber", page.getNumber(),
        "pageSize", page.getSize(),
        "sort", Map.of(
            "empty", s.isEmpty(),
            "unsorted", s.isUnsorted(),
            "sorted", s.isSorted()
        ),
        "offset", p.getOffset(),
        "unpaged", p.isUnpaged(),
        "paged", p.isPaged()
    ));
    m.put("sort", Map.of(
        "empty", s.isEmpty(),
        "unsorted", s.isUnsorted(),
        "sorted", s.isSorted()
    ));

    long inbound = page.getContent().stream().filter(r -> {
      if (r instanceof Map<?, ?> m1) {
        Object v = m1.get("ioDiscdVal");
        return v != null && (v.equals("수신") || v.equals("I") || v.equals("Inbound"));
      }
      return false;
    }).count();
    long outbound = page.getNumberOfElements() - inbound;
    m.put("inboundCount", inbound);
    m.put("outboundCount", outbound);

    return m;
  }
}
