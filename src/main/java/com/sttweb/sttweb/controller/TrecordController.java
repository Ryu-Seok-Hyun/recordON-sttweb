package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordScanService;
import com.sttweb.sttweb.service.TrecordService;
import com.sttweb.sttweb.repository.TmemberLinePermRepository;
import com.sttweb.sttweb.entity.TrecordTelListEntity;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;


@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private final TrecordService recordSvc;
  private final TrecordScanService scanSvc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;
  private final UserPermissionRepository userPermRepo;
  private final TrecordTelListRepository trecordTelListRepository;
  private final TmemberLinePermRepository memberLinePermRepo;

  // 인증 필수: 토큰 파싱 & 사용자 조회
  private Info requireLogin(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
    }
    String token = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    }
    return memberSvc.getMyInfoByUserId(jwtTokenProvider.getUserId(token));
  }

  // 내선번호 변환
  private String normalizeToFourDigit(String n) {
    if (n == null) return null;
    String d = n.replaceAll("[^0-9]", "");
    if (d.length() == 4) return d;
    if (d.length() == 3) return "0" + d;
    if (d.length() > 4) return d.substring(d.length() - 4);
    return null;
  }

  // 번호 형태 다양하게 매핑
  private List<String> makeExtensions(String num) {
    if (num == null) return Collections.emptyList();
    String d = num.replaceAll("[^0-9]", "");
    Set<String> s = new LinkedHashSet<>();
    if (!d.isEmpty()) {
      s.add(d);
      if (d.length() == 3) s.add("0" + d);
      if (d.length() > 4) s.add(d.substring(d.length() - 4));
    }
    return new ArrayList<>(s);
  }

  // 번호 화면 표시(내선/번호)
  private String convertToExtensionDisplay(String num) {
    if (num == null) return null;
    String t = num.trim();
    if (t.length() == 4) return t;
    if (t.contains("-")) {
      String last = t.substring(t.lastIndexOf('-') + 1);
      if (last.length() == 4) return last;
    }
    if (t.length() > 4) return t.substring(t.length() - 4);
    if (t.length() == 3) return "0" + t;
    return t;
  }

  // 본인/권한받은 내선 목록 조회
  private List<String> getAccessibleNumbers(String userId) {
    Set<String> numbers = new LinkedHashSet<>();
    Info me = memberSvc.getMyInfoByUserId(userId);

    if (me.getNumber() != null && !me.getNumber().trim().isEmpty()) {
      String n = normalizeToFourDigit(me.getNumber());
      if (n != null) numbers.add(n);
    }

    Map<Integer, String> lineIdToCallNum = trecordTelListRepository.findAll().stream()
        .collect(Collectors.toMap(TrecordTelListEntity::getId, TrecordTelListEntity::getCallNum));

    for (UserPermission perm : userPermRepo.findByMemberSeq(me.getMemberSeq())) {
      if (perm.getPermLevel() >= 2 && perm.getLineId() != null) {
        String ext = lineIdToCallNum.get(perm.getLineId());
        String n = normalizeToFourDigit(ext);
        if (n != null) numbers.add(n);
      }
    }
    return new ArrayList<>(numbers);
  }

  // 번호 권한 체크(레벨별)
  private boolean hasPermissionForNumber(String userId, String number, int reqLevel) {
    Info me = memberSvc.getMyInfoByUserId(userId);
    List<String> target = makeExtensions(number);

    if (me.getNumber() != null) {
      for (String ext : makeExtensions(me.getNumber())) {
        if (target.contains(ext)) return true;
      }
    }

    if (me.getMemberSeq() != null) {
      Map<Integer, String> lineIdToCallNum = trecordTelListRepository.findAll().stream()
          .collect(Collectors.toMap(TrecordTelListEntity::getId, TrecordTelListEntity::getCallNum));
      for (UserPermission perm : userPermRepo.findByMemberSeq(me.getMemberSeq())) {
        if (perm.getPermLevel() >= reqLevel && perm.getLineId() != null) {
          String ext = lineIdToCallNum.get(perm.getLineId());
          if (ext == null) continue;
          List<String> granted = makeExtensions(ext);
          for (String x : granted) {
            if (target.contains(x)) return true;
          }
        }
      }
    }
    return false;
  }

  // 메인 녹취 리스트 API
  @LogActivity(type = "record", activity = "조회", contents = "전체 녹취 조회")
  @GetMapping
  public ResponseEntity<Map<String, Object>> listAll(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size,
      @RequestParam(name = "direction", defaultValue = "ALL") String direction,
      @RequestParam(name = "numberKind", defaultValue = "ALL") String numberKind,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "start", required = false) String startStr,
      @RequestParam(name = "end", required = false) String endStr
  ) {

    // ★ 빈 문자열 보정
    if (!StringUtils.hasText(direction))  direction   = "ALL";
    if (!StringUtils.hasText(numberKind))  numberKind  = "ALL";

    // 수정: q **없을 때** PHONE → ALL (내선목록처럼)
    if (StringUtils.hasText(q)) {
           numberKind = "ALL";
        }



    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = (startStr != null && !startStr.isEmpty()) ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end   = (endStr   != null && !endStr.isEmpty()) ? LocalDateTime.parse(endStr,   fmt) : null;

    Info me   = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    Pageable reqPage = PageRequest.of(page, size);

    Page<TrecordDto> paged;
    if ("0".equals(lvl)) {
      // 관리자: 전체 + 필터
      paged = recordSvc.search(
          null, null,
          direction, numberKind, q,
          start, end,
          reqPage
      );
    }
    else if ("1".equals(lvl)) {
      // 지점장: 지점 내 전체 내선 + 필터
      List<String> nums = memberSvc
          .listUsersInBranch(me.getBranchSeq(), PageRequest.of(0, Integer.MAX_VALUE))
          .getContent().stream()
          .map(Info::getNumber)
          .filter(Objects::nonNull)
          .map(this::normalizeToFourDigit)
          .filter(Objects::nonNull)
          .toList();
      if (nums.isEmpty()) {
        paged = Page.empty(reqPage);
      } else {     // 전화번호 검색어 있을 때는 LIKE 검색으로
        if (StringUtils.hasText(q) && "PHONE".equalsIgnoreCase(numberKind)) {
          paged = recordSvc.search(
              null, null,
              direction, numberKind, q,
              start, end,
              reqPage
          );
        } else {
          paged = recordSvc.searchByMixedNumbers(
              nums,
              direction, numberKind, q,
              start, end,
              reqPage
          );
        }
      }
    }
    else if ("2".equals(lvl)) {
      // ▶ 일반 사용자: q가 있으면 콘텐츠 검색, 없으면 내선번호 검색
      if (q != null && !q.isBlank()) {
        // (1) 통화내용(q) 검색
        paged = recordSvc.search(
            null, null, direction, numberKind, q, start, end, reqPage
        );
      }
      else {
        // (2) 내선번호 검색
        List<String> nums = getAccessibleNumbers(me.getUserId());
        if (nums.isEmpty()) {
          paged = Page.empty(reqPage);
        } else {
          paged = recordSvc.searchByMixedNumbers(
              nums, direction, numberKind, q, start, end, reqPage
          );
        }
      }
    }
    else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    paged.getContent().forEach(rec -> {
      if (rec.getLineId() == null && rec.getNumber1() != null && rec.getNumber1().length() == 4) {
        trecordTelListRepository.findByCallNum(rec.getNumber1())
            .ifPresent(line -> rec.setLineId(line.getId()));
      }
      if (rec.getOwnerMemberSeq() == null && rec.getNumber1() != null) {
        memberSvc.getAllMembers().stream()
            .filter(m -> rec.getNumber1().equals(m.getNumber()))
            .findFirst()
            .ifPresent(m -> rec.setOwnerMemberSeq(m.getMemberSeq()));
      }
      if (rec.getOwnerMemberSeq() != null && rec.getLineId() != null) {
        Integer roleSeq = memberLinePermRepo.findRoleSeqByMemberSeqAndLineId(
            rec.getOwnerMemberSeq(),
            rec.getLineId()
        );
        rec.setRoleSeq(roleSeq);
      }
      boolean canL = "0".equals(lvl) || "1".equals(lvl)
          || ("2".equals(lvl) && hasPermissionForNumber(me.getUserId(), rec.getNumber1(), 3));
      try {
        Field f = TrecordDto.class.getDeclaredField("listenAuth");
        f.setAccessible(true);
        f.set(rec, canL ? "가능" : "불가능");
      } catch (Exception ignored) {
      }
    });

    if (me.getMaskFlag() != null && me.getMaskFlag() == 0)
      paged.getContent().forEach(TrecordDto::maskNumber2);

    long inCount = recordSvc.countByBranchAndDirection(null, "IN");
    long outCount = recordSvc.countByBranchAndDirection(null, "OUT");
    if ("1".equals(lvl)) {
      inCount = recordSvc.countByBranchAndDirection(me.getBranchSeq(), "IN");
      outCount = recordSvc.countByBranchAndDirection(me.getBranchSeq(), "OUT");
    } else if ("2".equals(lvl)) {
      inCount = outCount = paged.getTotalElements();
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("content", paged.getContent());
    body.put("totalElements", paged.getTotalElements());
    body.put("totalPages", paged.getTotalPages());
    body.put("size", paged.getSize());
    body.put("number", paged.getNumber());
    body.put("numberOfElements", paged.getNumberOfElements());
    body.put("empty", paged.isEmpty());
    body.put("inboundCount", inCount);
    body.put("outboundCount", outCount);
    body.put("first", paged.isFirst());
    body.put("last", paged.isLast());
    body.put("pageable", paged.getPageable());
    body.put("sort", paged.getSort());

    return ResponseEntity.ok(body);
  }

  // 번호로 녹취 검색
  @LogActivity(type = "record", activity = "조회", contents = "번호 검색")
  @GetMapping("/search")
  public ResponseEntity<Page<TrecordDto>> searchByNumbers(
      @RequestParam(value = "numbers",    required = false) String numbersCsv,
      @RequestParam(value = "number1",    required = false) String number1,
      @RequestParam(value = "number2",    required = false) String number2,
      @RequestParam(name = "direction",   defaultValue = "ALL") String direction,
      @RequestParam(name = "numberKind",  defaultValue = "ALL") String numberKind,
      @RequestParam(name = "q",           required = false)      String q,
      @RequestParam(name = "start",       required = false)      String startStr,
      @RequestParam(name = "end",         required = false)      String endStr,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size
  ) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = (startStr != null && !startStr.isEmpty()) ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end   = (endStr   != null && !endStr.isEmpty()) ? LocalDateTime.parse(endStr,   fmt) : null;

    Info me   = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    Pageable pr = PageRequest.of(page, size);

    Page<TrecordDto> paged;

    // q **없을 때** PHONE → ALL
       if (!StringUtils.hasText(q) && "PHONE".equalsIgnoreCase(numberKind)) {
          numberKind = "ALL";
         }

    // q가 있으면 무조건 ALL로
      if (StringUtils.hasText(q)) {
          numberKind = "ALL";
         }

    if ("0".equals(lvl) || "1".equals(lvl)) {
      // ADMIN/지점장: 전체 or CSV → 필터
      if (numbersCsv != null && !numbersCsv.isBlank()) {
        List<String> nums = Arrays.stream(numbersCsv.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
        paged = recordSvc.searchByMixedNumbers(
            nums,
            direction, numberKind, q,
            start, end,
            pr
        );
      }
      else if ((number1 != null && !number1.isBlank()) || (number2 != null && !number2.isBlank())) {
        paged = recordSvc.search(
            number1, number2,
            direction, numberKind, q,
            start, end,
            pr
        );
      }
      else {
        paged = recordSvc.search(
            null, null,
            direction, numberKind, q,
            start, end,
            pr
        );
      }
    }
    else if ("2".equals(lvl)) {
      List<String> accessible = getAccessibleNumbers(me.getUserId());
      if (accessible.isEmpty()) {
        paged = Page.empty(pr);
      } else {
        // 입력된 CSV → accessible 필터
        List<String> nums = (numbersCsv != null && !numbersCsv.isBlank())
            ? Arrays.stream(numbersCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .filter(accessible::contains)
            .toList()
            : accessible;

        if (nums.isEmpty()) {
          paged = Page.empty(pr);
        } else {
          paged = recordSvc.searchByMixedNumbers(nums, pr);
        }
      }
    }
    // ───────────────────────────────────────────────────────────
    else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    // 후처리 (번호 포맷, 권한, 마스킹 등)
    paged.getContent().forEach(rec -> {
      if (rec.getNumber1() != null) rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
      if (rec.getNumber2() != null) rec.setNumber2(convertToExtensionDisplay(rec.getNumber2()));
    });
    if (me.getMaskFlag() != null && me.getMaskFlag() == 0) {
      paged.getContent().forEach(TrecordDto::maskNumber2);
    }

    return ResponseEntity.ok(paged);
  }


  // 단건 녹취 조회
  @LogActivity(type = "record", activity = "조회", contents = "단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<TrecordDto> getById(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    TrecordDto dto = recordSvc.findById(id);

    if ("2".equals(lvl)) {
      boolean ok = hasPermissionForNumber(me.getUserId(), dto.getNumber1(), 2)
          || hasPermissionForNumber(me.getUserId(), dto.getNumber2(), 2);
      if (!ok) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "조회 권한이 없습니다.");
      }
    } else if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    if (dto.getNumber1() != null) dto.setNumber1(convertToExtensionDisplay(dto.getNumber1()));
    if (dto.getNumber2() != null) dto.setNumber2(convertToExtensionDisplay(dto.getNumber2()));

    if (me.getMaskFlag() != null && me.getMaskFlag() == 0) {
      dto.maskNumber2();
    }
    return ResponseEntity.ok(dto);
  }

  // 파일 스트리밍 청취(Range 지원)
  @LogActivity(type = "record", activity = "청취", contents = "녹취 청취")
  @GetMapping(value = "/{id}/listen", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<ResourceRegion> streamAudio(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestHeader HttpHeaders headers,
      HttpServletRequest request,
      @PathVariable("id") Integer id
  ) throws IOException {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    TrecordDto dto = recordSvc.findById(id);

    if ("2".equals(lvl)) {
      boolean ok = hasPermissionForNumber(me.getUserId(), dto.getNumber1(), 3)
          || hasPermissionForNumber(me.getUserId(), dto.getNumber2(), 3);
      if (!ok) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "청취 권한이 없습니다.");
      }
    } else if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    Resource audio = recordSvc.getFile(id);
    long length = audio.contentLength();
    List<HttpRange> ranges = headers.getRange();
    ResourceRegion region = ranges.isEmpty()
        ? new ResourceRegion(audio, 0, Math.min(1024 * 1024, length))
        : ranges.get(0).toResourceRegion(audio);

    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(MediaTypeFactory.getMediaType(audio).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .eTag("\"" + id + "-" + region.getPosition() + "\"")
        .body(region);
  }

  // 녹취 파일 다운로드
  @LogActivity(type = "record", activity = "다운로드", contents = "녹취 다운로드")
  @GetMapping("/{id}/download")
  public ResponseEntity<Resource> downloadById(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      HttpServletRequest request,
      @PathVariable("id") Integer id
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    TrecordDto dto = recordSvc.findById(id);

    if ("2".equals(lvl)) {
      boolean ok = hasPermissionForNumber(me.getUserId(), dto.getNumber1(), 4)
          || hasPermissionForNumber(me.getUserId(), dto.getNumber2(), 4);
      if (!ok) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다운로드 권한이 없습니다.");
      }
    } else if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    Resource file = recordSvc.getFile(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
        .body(file);
  }

  // 지점별 녹취 목록
  @GetMapping("/branch/{branchSeq}")
  public ResponseEntity<Page<TrecordDto>> listByBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("branchSeq") Integer branchSeq,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    Info me = requireLogin(authHeader);

    if (!"0".equals(me.getUserLevel())
        && !("1".equals(me.getUserLevel()) && me.getBranchSeq().equals(branchSeq))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }

    Pageable pr = PageRequest.of(page, size);
    Page<TrecordDto> result = recordSvc.findAllByBranch(branchSeq, pr);

    result.getContent().forEach(rec -> {
      if (rec.getNumber1() != null) rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
      if (rec.getNumber2() != null) rec.setNumber2(convertToExtensionDisplay(rec.getNumber2()));
    });

    return ResponseEntity.ok(result);
  }
}
