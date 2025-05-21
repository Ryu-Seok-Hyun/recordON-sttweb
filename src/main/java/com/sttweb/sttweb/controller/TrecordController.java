package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.PermissionService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

  private final TrecordService recordSvc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;
  private final PermissionService permService;

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

  /**
   * 전체 녹취 + 필터링
   * direction=ALL|IN|OUT,
   * numberKind=ALL|PHONE|EXT,
   * q=검색어,
   * start/end = yyyy-MM-dd HH:mm
   */
  @LogActivity(type = "record", activity = "조회", contents = "전체 녹취 조회")
  @GetMapping
  public ResponseEntity<Page<TrecordDto>> listAll(
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
    LocalDateTime start = startStr != null ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end   = endStr   != null ? LocalDateTime.parse(endStr,   fmt) : null;

    Info me = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    PageRequest pr = PageRequest.of(page, size);

    Page<TrecordDto> paged = (roleSeq < 2)
        ? recordSvc.search(me.getNumber(), me.getNumber(),
        direction, numberKind, q, start, end, pr)
        : recordSvc.search(null, null,
            direction, numberKind, q, start, end, pr);

    return ResponseEntity.ok(paged);
  }

  /** 번호 검색 (legacy) */
  @LogActivity(type = "record", activity = "조회", contents = "번호 검색")
  @GetMapping("/search")
  public ResponseEntity<Page<TrecordDto>> searchByNumber(
      @RequestParam(name = "number1") String number1,
      @RequestParam(name = "number2") String number2,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    Info me = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    PageRequest pr = PageRequest.of(page, size);

    if (roleSeq >= 2) {
      return ResponseEntity.ok(recordSvc.searchByNumber(number1, number2, pr));
    } else {
      String myNum = me.getNumber();
      if (!myNum.equals(number1) && !myNum.equals(number2)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 자료 외에 검색할 수 없습니다.");
      }
      return ResponseEntity.ok(recordSvc.searchByNumber(number1, number2, pr));
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
    if (roleSeq < 2 && !me.getNumber().equals(dto.getNumber1()) && !me.getNumber().equals(dto.getNumber2())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 자료 외에 조회할 수 없습니다.");
    }
    return ResponseEntity.ok(dto);
  }

  /** 청취 (LISTEN 이상) */
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
        .contentType(MediaTypeFactory.getMediaType(audio).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .eTag("\"" + id + "-" + region.getPosition() + "\"")
        .body(region);
  }

  /** 다운로드 (DOWNLOAD 이상) */
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
    boolean isOwner        = me.getNumber().equals(dto.getNumber1()) || me.getNumber().equals(dto.getNumber2());
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
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
        .body(file);
  }
}
