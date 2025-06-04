package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.PermissionService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordScanService;
import com.sttweb.sttweb.service.TrecordService;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private final TrecordService     recordSvc;
  private final TrecordScanService scanSvc;
  private final TmemberService     memberSvc;
  private final JwtTokenProvider   jwtTokenProvider;
  private final PermissionService  permService;

  private String extractToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
    }
    String token = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    }
    return token;
  }

  private Info getCurrentUser(String token) {
    String userId = jwtTokenProvider.getUserId(token);
    return memberSvc.getMyInfoByUserId(userId);
  }

  private Info requireLogin(String authHeader) {
    String token = extractToken(authHeader);
    return getCurrentUser(token);
  }

  // ---------------------------------------
  // 0) 신규 녹취 스캔 → DB 저장
  //    → GET /api/records/scan
  // ---------------------------------------
  @LogActivity(type = "record", activity = "등록", contents = "디스크 스캔하여 DB 저장")
  @GetMapping("/scan")
  public ResponseEntity<Map<String, Object>> scanAndSaveNewRecords(
      @RequestHeader(value = "Authorization", required = false) String authHeader
  ) {
    // (1) 로그인/토큰 확인
    Info me = requireLogin(authHeader);

    // (2) 실제 스캔 & 저장
    Map<String, Object> resp = new HashMap<>();
    try {
      int inserted = scanSvc.scanAndSaveNewRecords();
      resp.put("inserted", inserted);
      resp.put("message", "신규 녹취 " + inserted + "건이 DB에 저장되었습니다.");
      return ResponseEntity.ok(resp);
    } catch (IOException e) {
      resp.put("inserted", 0);
      resp.put("message", "스캔 중 오류 발생: " + e.getMessage());
      return ResponseEntity.status(500).body(resp);
    }
  }

  // ---------------------------------------
  // 1) 전체 녹취 + 필터링 + IN/OUT 카운트
  // ---------------------------------------
  @LogActivity(type = "record", activity = "조회", contents = "전체 녹취 조회")
  @GetMapping
  public ResponseEntity<Map<String,Object>> listAll(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size,
      @RequestParam(name = "direction", defaultValue = "ALL") String direction,
      @RequestParam(name = "numberKind", defaultValue = "ALL") String numberKind,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "start", required = false) String startStr,
      @RequestParam(name = "end",   required = false) String endStr
  ) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = (startStr != null ? LocalDateTime.parse(startStr, fmt) : null);
    LocalDateTime end   = (endStr   != null ? LocalDateTime.parse(endStr,   fmt) : null);

    Info me = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    PageRequest pr = PageRequest.of(page, size);

    // q에 쉼표가 있으면 multi-number 모드
    Page<TrecordDto> paged;
    boolean multi = (q != null && q.contains(","));
    if (multi) {
      List<String> numbers = Arrays.stream(q.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .toList();
      if (roleSeq < 2) {
        numbers = numbers.stream()
            .filter(n -> n.equals(me.getNumber()))
            .toList();
      }
      paged = recordSvc.searchByNumbers(numbers, pr);
    }
    else {
      paged = (roleSeq < 2)
          ? recordSvc.search(me.getNumber(), me.getNumber(),
          direction, numberKind, q, start, end, pr)
          : recordSvc.search(null, null,
              direction, numberKind, q, start, end, pr);
    }

    long inCount, outCount;
    if (!multi) {
      inCount = recordSvc.search(
          (roleSeq < 2 ? me.getNumber() : null),
          (roleSeq < 2 ? me.getNumber() : null),
          "IN", numberKind, q, start, end,
          PageRequest.of(0,1)
      ).getTotalElements();
      outCount = recordSvc.search(
          (roleSeq < 2 ? me.getNumber() : null),
          (roleSeq < 2 ? me.getNumber() : null),
          "OUT", numberKind, q, start, end,
          PageRequest.of(0,1)
      ).getTotalElements();
    } else {
      inCount  = paged.getTotalElements();
      outCount = paged.getTotalElements();
    }

    Map<String,Object> body = new LinkedHashMap<>();
    body.put("content",          paged.getContent());
    body.put("totalElements",    paged.getTotalElements());
    body.put("totalPages",       paged.getTotalPages());
    body.put("size",             paged.getSize());
    body.put("number",           paged.getNumber());
    body.put("numberOfElements", paged.getNumberOfElements());
    body.put("empty",            paged.isEmpty());
    body.put("inboundCount",     inCount);
    body.put("outboundCount",    outCount);
    body.put("first",            paged.isFirst());
    body.put("last",             paged.isLast());
    body.put("pageable",         paged.getPageable());
    body.put("sort",             paged.getSort());

    return ResponseEntity.ok(body);
  }

  /**
   * 번호 검색 (다중/단일)
   */
  @LogActivity(type = "record", activity = "조회", contents = "번호 검색")
  @GetMapping("/search")
  public ResponseEntity<Page<TrecordDto>> searchByNumbers(
      @RequestParam(value = "numbers", required = false) String numbersCsv,
      @RequestParam(value = "number1",  required = false) String number1,
      @RequestParam(value = "number2",  required = false) String number2,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size
  ) {
    Info me = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    Pageable pr = PageRequest.of(page, size);

    // 1) 다중 검색 모드
    if (numbersCsv != null) {
      List<String> nums = Arrays.stream(numbersCsv.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .toList();

      if (roleSeq < 2 && nums.stream().anyMatch(n -> !n.equals(me.getNumber()))) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "본인 자료만 검색 가능합니다.");
      }
      return ResponseEntity.ok(recordSvc.searchByNumbers(nums, pr));
    }

    // 2) 단일 검색 모드
    if (number1 == null && number2 == null) {
      return ResponseEntity.ok(recordSvc.findAll(pr));
    }
    if (roleSeq < 2) {
      String myNum = me.getNumber();
      if ((number1 != null && !number1.equals(myNum)) ||
          (number2 != null && !number2.equals(myNum))) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "본인 자료 외에 검색할 수 없습니다.");
      }
    }
    if (number1 != null && number2 != null) {
      return ResponseEntity.ok(recordSvc.searchByNumber(number1, number2, pr));
    } else if (number1 != null) {
      return ResponseEntity.ok(recordSvc.searchByNumber(number1, null, pr));
    } else {
      return ResponseEntity.ok(recordSvc.searchByNumber(null, number2, pr));
    }
  }

  /** 단건 조회 */
  @LogActivity(type = "record", activity = "조회", contents = "단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<TrecordDto> getById(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    Info me = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    TrecordDto dto = recordSvc.findById(id);
    if (roleSeq < 2
        && !me.getNumber().equals(dto.getNumber1())
        && !me.getNumber().equals(dto.getNumber2())
    ) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 자료 외에 조회할 수 없습니다.");
    }
    return ResponseEntity.ok(dto);
  }

  /** 청취 (LISTEN 이상 권한) */
  @LogActivity(type = "record", activity = "청취", contents = "녹취 청취")
  @GetMapping(value = "/{id}/listen", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<ResourceRegion> streamAudio(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestHeader HttpHeaders headers,
      @PathVariable("id") Integer id
  ) throws IOException {
    Info me = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    if (roleSeq < 3) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "청취 권한이 없습니다.");
    }

    Resource audio = recordSvc.getFile(id);
    long contentLength = audio.contentLength();
    List<HttpRange> ranges = headers.getRange();
    ResourceRegion region;
    if (ranges.isEmpty()) {
      long chunk = Math.min(1024 * 1024, contentLength);
      region = new ResourceRegion(audio, 0, chunk);
    } else {
      HttpRange range = ranges.get(0);
      long startPos = range.getRangeStart(contentLength);
      long endPos   = range.getRangeEnd(contentLength);
      region = new ResourceRegion(audio, startPos, endPos - startPos + 1);
    }

    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(MediaTypeFactory.getMediaType(audio)
            .orElse(MediaType.APPLICATION_OCTET_STREAM))
        .eTag("\"" + id + "-" + region.getPosition() + "\"")
        .body(region);
  }

  /** 다운로드 (DOWNLOAD 이상 권한) */
  @LogActivity(type = "record", activity = "다운로드", contents = "녹취 다운로드")
  @GetMapping("/{id}/download")
  public ResponseEntity<Resource> downloadById(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("id") Integer id
  ) {
    Info me = requireLogin(authHeader);
    TrecordDto dto = recordSvc.findById(id);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());

    boolean hasDownloadRole = roleSeq >= 4;
    boolean isOwner        = me.getNumber().equals(dto.getNumber1())
        || me.getNumber().equals(dto.getNumber2());
    boolean isBranchAdmin  = "1".equals(me.getUserLevel())
        && me.getBranchSeq() != null
        && me.getBranchSeq().equals(dto.getBranchSeq());
    boolean hasGrant       = dto.getOwnerMemberSeq() != null
        && permService.hasLevel(me.getMemberSeq(), dto.getOwnerMemberSeq(), 4);

    if (!(hasDownloadRole || isOwner || isBranchAdmin || hasGrant)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다운로드 권한이 없습니다.");
    }

    Resource file = recordSvc.getFile(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + file.getFilename() + "\"")
        .body(file);
  }
}
