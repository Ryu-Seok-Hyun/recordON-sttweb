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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

  // ── auth 동일화 ──
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

  // ── ES filenames → DB 조인 ──
  @GetMapping("/search/join")
  public ResponseEntity<Map<String, Object>> joinRecordsByQueries(
      @RequestParam(name = "s") List<String> terms,
      @RequestParam(name = "page",  defaultValue = "0")  int page,
      @RequestParam(name = "size",  defaultValue = "20") int size,
      @RequestParam(name = "fpage", defaultValue = "0")  int fpage,
      @RequestParam(name = "fsize", defaultValue = "1000") int fsize,
      @RequestParam(name = "q",            required = false) String q,
      @RequestParam(name = "number",       required = false) String number,
      @RequestParam(name = "numberKind",   defaultValue = "ALL") String numberKind,
      @RequestParam(name = "direction",    defaultValue = "ALL") String direction,
      @RequestParam(name = "start",        required = false) String startStr,
      @RequestParam(name = "end",          required = false) String endStr,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    // 0) 사용자 인증
    Info me = requireLogin(authHeader);

    // 1) ES에서 filename 유니크 추출
    Map<String, Object> fnPage = sttSearchService.searchFilenamesArray(terms, fpage, fsize);
    @SuppressWarnings("unchecked")
    List<String> esNames = (List<String>) fnPage.getOrDefault("filenames", Collections.emptyList());

    // 2) 소문자 basename.wav 로 정규화
    Set<String> baseSet = new LinkedHashSet<>();
    for (String s : esNames) {
      if (s == null) continue;
      String t = s.trim();
      if (t.isEmpty()) continue;
      t = t.replace('\\','/');
      int i = t.lastIndexOf('/');
      String name = (i >= 0 ? t.substring(i + 1) : t).toLowerCase();
      if (!name.endsWith(".wav")) name = name + ".wav";
      baseSet.add(name);
    }
    List<String> basenames = new ArrayList<>(baseSet);

    // 3) 기간 파싱
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = (startStr != null && !startStr.isBlank()) ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end   = (endStr   != null && !endStr.isBlank())   ? LocalDateTime.parse(endStr,   fmt) : null;

    // 4) 번호 파라미터 통합 및 kind 자동 보정
    String numArg = (number != null && !number.isBlank()) ? number : q; // 화면 q를 번호로도 쓰는 케이스 지원
    String nk = (numberKind == null || numberKind.isBlank()) ? "ALL" : numberKind;
    if ("ALL".equalsIgnoreCase(nk) && numArg != null && !numArg.isBlank()) {
      String digits = numArg.replaceAll("[^0-9]", "");
      if (digits.length() >= 3 && digits.length() <= 4) nk = "EXT";
      else if (digits.length() >= 5)                   nk = "PHONE";
    }

    // 5) "s 제외" 동일 필터로 DB 전체 건수 계산 (총 건)
    long recordsTotal        = trecordService.countByFilters("ALL", nk, numArg, start, end);
    long recordsInboundTotal = trecordService.countByFilters("IN",  nk, numArg, start, end);
    long recordsOutboundTotal= trecordService.countByFilters("OUT", nk, numArg, start, end);

    // 6) ES hit 0개면 즉시 반환
    if (basenames.isEmpty()) {
      Map<String, Object> body = buildEmptyPageResponse(page, size, esNames);
      body.put("recordsTotal", recordsTotal);
      body.put("recordsInboundTotal", recordsInboundTotal);
      body.put("recordsOutboundTotal", recordsOutboundTotal);
      // 검색결과(=STT 조인) 총계는 0
      body.put("resultTotal", 0L);
      body.put("inboundCount", 0L);
      body.put("outboundCount", 0L);
      return ResponseEntity.ok(body);
    }

    // 7) DB 조인 + 필터 + 정렬(페이지)
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "callStartDateTime"));
    Page<TrecordDto> recordPage = trecordService.searchByAudioBasenamesWithFilters(
        basenames, direction, nk, numArg, start, end, pageable);

    // 8) STT 결과 "전체" 수신/발신 총계 (direction과 무관하게 계산)
    PageRequest one = PageRequest.of(0, 1); // count 목적 최소 페이지
    long resultInboundTotal  = trecordService
        .searchByAudioBasenamesWithFilters(basenames, "IN",  nk, numArg, start, end, one)
        .getTotalElements();
    long resultOutboundTotal = trecordService
        .searchByAudioBasenamesWithFilters(basenames, "OUT", nk, numArg, start, end, one)
        .getTotalElements();
    long resultTotal = resultInboundTotal + resultOutboundTotal; // (= direction=ALL일 때 totalElements)

    // 9) STT 활성화/JSON 생성여부 후처리
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
        String afd = Optional.ofNullable(rec.getAudioFileDir()).orElse("")
            .replace("\\", "/")
            .replaceFirst("^\\.\\./+", "");
        String fname = afd.substring(afd.lastIndexOf('/') + 1);
        rec.setJsonExists(recOnDataService.isJsonGenerated(dateDir, fname));
      } else {
        rec.setJsonExists(false);
      }
    });

    // 10) 슈퍼유저가 아니면 jsonExists 감춤
    if (!"3".equals(me.getUserLevel())) {
      recordPage.getContent().forEach(r -> r.setJsonExists(null));
    }

    // 11) 응답
    Map<String, Object> body = buildPageResponse(recordPage); // 페이지 메타 + (페이지 기준) inbound/outbound 포함
    body.put("filenames", esNames);

    // 페이지 카운트 보존
    long pageInbound   = ((Number) body.getOrDefault("inboundCount", 0)).longValue();
    long pageOutbound  = ((Number) body.getOrDefault("outboundCount", 0)).longValue();
    body.put("pageInboundCount", pageInbound);
    body.put("pageOutboundCount", pageOutbound);

    // 화면에서 쓰는 기존 키는 "총계"로 덮어쓰기 (하위호환)
    body.put("inboundCount",  resultInboundTotal);
    body.put("outboundCount", resultOutboundTotal);

    // 총 건과 검색결과 총계
    body.put("recordsTotal",          recordsTotal);          // s 제외 전체
    body.put("recordsInboundTotal",   recordsInboundTotal);   // s 제외 전체 수신
    body.put("recordsOutboundTotal",  recordsOutboundTotal);  // s 제외 전체 발신
    body.put("resultTotal",           resultTotal);           // s 포함 검색결과 전체(수신+발신)

    return ResponseEntity.ok(body);
  }



  /** 텍스트→filename→DB 페이징(매핑에 맞춰 nested 미사용) */
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

  /** filename 유니크만 */
  @GetMapping("/search/filenames")
  public ResponseEntity<Map<String, Object>> searchFilenames(
      @RequestParam(name = "s", required = false, defaultValue = "") String s,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    return ResponseEntity.ok(sttSearchService.searchFilenames(s, page, size));
  }

  /** s 배열(OR) → filename 유니크만 */
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

  // ───────── helpers ─────────

  private Map<String, Object> buildEmptyPageResponse(int page, int size, List<String> esNames) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("content", List.of());
    m.put("totalElements", 0L);
    m.put("totalPages", 0);
    m.put("size", size);
    m.put("number", page);
    m.put("numberOfElements", 0);
    m.put("empty", true);
    m.put("first", true);
    m.put("last", true);
    m.put("inboundCount", 0L);
    m.put("outboundCount", 0L);
    m.put("filenames", esNames);
    return m;
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
      if (r instanceof TrecordDto dto) {
        String v = dto.getIoDiscdVal();
        return "수신".equals(v) || "I".equalsIgnoreCase(v) || "Inbound".equalsIgnoreCase(v);
      } else if (r instanceof Map<?,?> m1) {
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
