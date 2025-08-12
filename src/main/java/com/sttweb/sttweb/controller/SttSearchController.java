// src/main/java/com/sttweb/sttweb/controller/SttSearchController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.service.SttSearchService;
import com.sttweb.sttweb.service.TrecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/stt")
@RequiredArgsConstructor
public class SttSearchController {

  private final SttSearchService sttSearchService;
  private final TrecordService trecordService;

  /**
   * ES filenames → DB 조인 결과 반환 (한 번에)
   * - s: 키워드(들)
   * - number / q: 내선/번호 (number가 없으면 q를 fallback으로 사용)
   * - numberKind: EXT | PHONE | ALL (빈 문자열이면 ALL, 3~4자리면 자동 EXT)
   * - direction: ALL | IN | OUT (빈 문자열이면 ALL)
   */
  @GetMapping("/search/join")
  public ResponseEntity<Map<String, Object>> joinRecordsByQueries(
      @RequestParam(name = "s") List<String> terms,
      @RequestParam(name = "page",  defaultValue = "0")  int page,
      @RequestParam(name = "size",  defaultValue = "20") int size,
      @RequestParam(name = "fpage", defaultValue = "0")  int fpage,
      @RequestParam(name = "fsize", defaultValue = "1000") int fsize,
      @RequestParam(name = "q",            required = false) String q,
      @RequestParam(name = "number",       required = false) String number,
      @RequestParam(name = "numberKind",   required = false) String numberKind,
      @RequestParam(name = "direction",    required = false) String direction,
      @RequestParam(name = "start",        required = false) String startStr,   // yyyy-MM-dd HH:mm
      @RequestParam(name = "end",          required = false) String endStr
  ) {
    // 0) 파라미터 정규화
    String numArg = StringUtils.hasText(number) ? number : (StringUtils.hasText(q) ? q : null);
    String dir    = StringUtils.hasText(direction)   ? direction   : "ALL";
    String nk     = StringUtils.hasText(numberKind)  ? numberKind  : "ALL";

    // 4자리 이하라면 내선으로 간주 (nk가 비었거나 ALL인 경우에만 보정)
    if (("ALL".equalsIgnoreCase(nk) || !StringUtils.hasText(nk)) && StringUtils.hasText(numArg)) {
      String digits = numArg.replaceAll("[^0-9]", "");
      if (digits.length() <= 4) nk = "EXT";
      else nk = "ALL";
    }

    // 1) ES에서 filename 유니크 추출
    Map<String, Object> fnPage = sttSearchService.searchFilenamesArray(terms, fpage, fsize);
    @SuppressWarnings("unchecked")
    List<String> esNames = (List<String>) fnPage.getOrDefault("filenames", Collections.emptyList());

    // 2) 소문자 basename.wav 로 정규화
    List<String> basenames = esNames.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.replace('\\','/'))
        .map(s -> { int i = s.lastIndexOf('/'); return (i >= 0 ? s.substring(i + 1) : s); })
        .map(String::toLowerCase)
        .map(s -> s.endsWith(".wav") ? s : (s + ".wav"))
        .distinct()
        .toList();

    // 3) 기간 파싱
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = (StringUtils.hasText(startStr) ? LocalDateTime.parse(startStr, fmt) : null);
    LocalDateTime end   = (StringUtils.hasText(endStr)   ? LocalDateTime.parse(endStr,   fmt) : null);

    // 4) 페이지 정보
    Pageable pageable = PageRequest.of(page, size,
        Sort.by(Sort.Direction.DESC, "callStartDateTime"));

    // basenames 없으면 바로 빈 페이지 반환
    Page<?> recordPage = basenames.isEmpty()
        ? Page.empty(pageable)
        : trecordService.searchByAudioBasenamesWithFilters(
            basenames, dir, nk, numArg, start, end, pageable);

    // 5) 응답
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
    // 서비스 시그니처에 맞춰 호출 (필요 시 searchByAudioFileNames 로 교체)
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

    // 안정적인 입/발신 카운트
    long inbound = page.getContent().stream().filter(r -> {
      if (r instanceof TrecordDto dto) {
        String v = dto.getIoDiscdVal();
        return "수신".equals(v) || "I".equalsIgnoreCase(v) || "Inbound".equalsIgnoreCase(v);
      } else if (r instanceof Map<?, ?> m1) {
        Object v = m1.get("ioDiscdVal");
        return v != null && (
            "수신".equals(v) || "I".equalsIgnoreCase(v.toString()) || "Inbound".equalsIgnoreCase(v.toString())
        );
      }
      return false;
    }).count();
    long outbound = page.getNumberOfElements() - inbound;

    m.put("inboundCount", inbound);
    m.put("outboundCount", outbound);
    return m;
  }
}
