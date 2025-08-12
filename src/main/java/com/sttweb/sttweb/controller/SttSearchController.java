// src/main/java/com/sttweb/sttweb/controller/SttSearchController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.RecOnDataService;
import com.sttweb.sttweb.service.SttSearchService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/stt")
@RequiredArgsConstructor
public class SttSearchController {

  private final SttSearchService sttSearchService;
  private final TrecordService trecordService;
  private final RecOnDataService recOnDataService;   // STT/JSON 후처리
  private final JwtTokenProvider jwtTokenProvider;   // 권한 확인용
  private final TmemberService memberService;        // 권한 확인용

  // 간단한 토큰 파서 (records 컨트롤러와 동일한 방식)
  private Info requireLogin(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer "))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
    String token = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    String userId = jwtTokenProvider.getUserId(token);
    Info me = memberService.getMyInfoByUserId(userId);
    if (me == null)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다.");
    return me;
  }

  // ES filenames → DB 조인 결과 반환
  @GetMapping("/search/join")
  public ResponseEntity<Map<String, Object>> joinRecordsByQueries(
      @RequestParam(name = "s") List<String> terms,
      @RequestParam(name = "page",  defaultValue = "0")  int page,
      @RequestParam(name = "size",  defaultValue = "20") int size,
      @RequestParam(name = "fpage", defaultValue = "0")  int fpage,
      @RequestParam(name = "fsize", defaultValue = "1000") int fsize,
      @RequestParam(name = "q",            required = false) String q,
      @RequestParam(name = "number",       required = false) String number,
      @RequestParam(name = "numberKind",   defaultValue = "ALL") String numberKind, // EXT|PHONE|ALL
      @RequestParam(name = "direction",    defaultValue = "ALL") String direction,  // ALL|IN|OUT
      @RequestParam(name = "start",        required = false) String startStr,       // yyyy-MM-dd HH:mm
      @RequestParam(name = "end",          required = false) String endStr,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    // 로그인 사용자
    Info me = requireLogin(authHeader);

    // 1) ES에서 filename 유니크 추출
    Map<String, Object> fnPage = sttSearchService.searchFilenamesArray(terms, fpage, fsize);
    @SuppressWarnings("unchecked")
    List<String> esNames = (List<String>) fnPage.getOrDefault("filenames", Collections.emptyList());

    // 2) 소문자 basename.wav 로 정규화
    List<String> basenames = esNames.stream()
        .filter(Objects::nonNull)
        .map(String::trim).filter(s -> !s.isEmpty())
        .map(s -> s.replace('\\','/'))
        .map(s -> { int i = s.lastIndexOf('/'); return (i >= 0 ? s.substring(i + 1) : s); })
        .map(String::toLowerCase)
        .map(s -> s.endsWith(".wav") ? s : (s + ".wav"))
        .distinct()
        .toList();

    // 3) 기간 파싱
    var fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    var start = (startStr != null && !startStr.isBlank()) ? java.time.LocalDateTime.parse(startStr, fmt) : null;
    var end   = (endStr   != null && !endStr.isBlank())   ? java.time.LocalDateTime.parse(endStr,   fmt) : null;

    // 4) DB 조인 + 필터 + 정렬
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "callStartDateTime"));
    @SuppressWarnings("unchecked")
    Page<TrecordDto> recordPage =
        (Page<TrecordDto>) trecordService.searchByAudioBasenamesWithFilters(
            basenames, direction, numberKind, number, start, end, pageable);

    // 5) STT 활성화/JSON 생성여부 후처리
    Map<String, Integer> extSttMap = recOnDataService.parseSttStatusFromIni();
    recordPage.getContent().forEach(rec -> {
      // sttEnabled
      String ext = rec.getNumber1();
      if (ext != null) ext = ext.replaceAll("^0+", "");
      rec.setSttEnabled(extSttMap.getOrDefault(ext, 0));

      // jsonExists
      String csdt = rec.getCallStartDateTime();
      if (csdt != null) {
        String dateDir = csdt.substring(0, 10).replace("-", "");
        String afd = rec.getAudioFileDir()
            .replace("\\", "/")
            .replaceFirst("^\\.\\./+", "");
        String fname = afd.substring(afd.lastIndexOf('/') + 1);
        rec.setJsonExists(recOnDataService.isJsonGenerated(dateDir, fname));
      } else {
        rec.setJsonExists(false);
      }
    });

    // 6) 슈퍼유저가 아니면 jsonExists 감추기 (records API와 동일)
    if (!"3".equals(me.getUserLevel())) {
      recordPage.getContent().forEach(r -> r.setJsonExists(null));
    }

    // 7) 응답
    Map<String, Object> body = buildPageResponse(recordPage);
    body.put("filenames", esNames);
    return ResponseEntity.ok(body);
  }

  /** 메인 STT 텍스트 검색 (변경 없음) */
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

  /** s=text → filename만 */
  @GetMapping("/search/filenames")
  public ResponseEntity<Map<String, Object>> searchFilenames(
      @RequestParam(name = "s", required = false, defaultValue = "") String s,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    return ResponseEntity.ok(sttSearchService.searchFilenames(s, page, size));
  }

  /** s 배열(OR) → filename만 */
  @GetMapping("/search/filenames/array")
  public ResponseEntity<Map<String, Object>> searchFilenamesArray(
      @RequestParam(name = "s") List<String> queries,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    return ResponseEntity.ok(sttSearchService.searchFilenamesArray(queries, page, size));
  }

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
        "sort", Map.of("empty", s.isEmpty(), "unsorted", s.isUnsorted(), "sorted", s.isSorted()),
        "offset", p.getOffset(),
        "unpaged", p.isUnpaged(),
        "paged", p.isPaged()
    ));
    m.put("sort", Map.of("empty", s.isEmpty(), "unsorted", s.isUnsorted(), "sorted", s.isSorted()));

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
