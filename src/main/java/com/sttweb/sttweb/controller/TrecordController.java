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
      @RequestParam(name="number", required=false) String numberParam,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "audioFiles", required = false) String audioFilesCsv,
      @RequestParam(name = "page",       defaultValue = "0")   int    page,
      @RequestParam(name = "size",       defaultValue = "10")  int    size,
      @RequestParam(name = "direction",  defaultValue = "ALL") String directionParam,
      @RequestParam(name = "numberKind", defaultValue = "ALL") String numberKindParam,
      @RequestParam(name = "q",          required = false)    String qParam,
      @RequestParam(name = "start",      required = false)    String startStr,
      @RequestParam(name = "end",        required = false)    String endStr
  ) {
    // 0) 인증 & 페이징
    Info me = requireLogin(authHeader);
    Pageable reqPage = PageRequest.of(page, size);

    // ① 날짜 파싱
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = StringUtils.hasText(startStr)
        ? LocalDateTime.parse(startStr, fmt)
        : null;
    LocalDateTime end   = StringUtils.hasText(endStr)
        ? LocalDateTime.parse(endStr,   fmt)
        : null;


    // ─── 전화번호 끝자리 검색 분기 ───
// numberParam(사용자가 입력한 검색어)이 있으면 무조건 전화번호 끝자리 검색으로 처리
    if (StringUtils.hasText(numberParam)) {
      // 1) 서비스 호출
      Page<TrecordDto> paged = recordSvc.searchByPhoneEnding(numberParam, reqPage);

      // 2) 후처리: 내선 표시, 마스킹, lineId, listenAuth 설정
      paged.getContent().forEach(rec -> {
        if (rec.getNumber1() != null)
          rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
        if (me.getMaskFlag()!=null && me.getMaskFlag()==0)
          rec.maskNumber2();
        if (rec.getLineId()==null
            && rec.getNumber1()!=null
            && rec.getNumber1().length()==4) {
          trecordTelListRepository.findByCallNum(rec.getNumber1())
              .map(TrecordTelListEntity::getId)
              .ifPresent(rec::setLineId);
        }
        boolean canListen = "0".equals(me.getUserLevel())
            || "1".equals(me.getUserLevel())
            || ("2".equals(me.getUserLevel())
            && hasPermissionForNumber(me.getUserId(), rec.getNumber1(), 3));
        try {
          Field f = TrecordDto.class.getDeclaredField("listenAuth");
          f.setAccessible(true);
          f.set(rec, canListen ? "가능" : "불가능");
        } catch (Exception ignore) {}
      });

      // 3) IN/OUT 건수 계산
      long inboundCount  = paged.getContent().stream()
          .filter(r -> "수신".equals(r.getIoDiscdVal())).count();
      long outboundCount = paged.getContent().stream()
          .filter(r -> "발신".equals(r.getIoDiscdVal())).count();

      // 4) 결과 바디 구성
      Map<String,Object> body = new LinkedHashMap<>();
      body.put("content",       paged.getContent());
      body.put("totalElements", paged.getTotalElements());
      body.put("totalPages",    paged.getTotalPages());
      body.put("size",          paged.getSize());
      body.put("number",        paged.getNumber());
      body.put("inboundCount",  inboundCount);
      body.put("outboundCount", outboundCount);
      body.put("empty",         paged.isEmpty());
      body.put("first",         paged.isFirst());
      body.put("last",          paged.isLast());
      body.put("pageable",      paged.getPageable());
      body.put("sort",          paged.getSort());

      return ResponseEntity.ok(body);
    }



    Page<TrecordDto> paged;
    long inboundCount;
    long outboundCount;
    Pageable one = PageRequest.of(0, 1); // 카운트 조회용

    // 검색 로직 통합: numberParam과 qParam을 하나의 검색어로 통합하여 처리합니다.
    String searchQuery = StringUtils.hasText(numberParam) ? numberParam : qParam;
    String searchNumberKind = StringUtils.hasText(numberParam) ? numberKindParam : (StringUtils.hasText(qParam) ? "ALL" : "ALL");

    // ② audioFiles 분기 (우선 처리)
    if (StringUtils.hasText(audioFilesCsv)) {
      List<String> audioFiles = Arrays.stream(audioFilesCsv.split(","))
          .map(String::trim).filter(s -> !s.isEmpty()).toList();

      List<TrecordDto> allResults = recordSvc.searchByAudioFileNames(audioFiles, Pageable.unpaged()).getContent();

      // 메모리상에서 필터링 수행
      List<TrecordDto> filtered = allResults.stream()
          .filter(rec -> {
            if (!"ALL".equalsIgnoreCase(directionParam)) {
              if ("IN".equalsIgnoreCase(directionParam))  return "수신".equals(rec.getIoDiscdVal());
              if ("OUT".equalsIgnoreCase(directionParam)) return "발신".equals(rec.getIoDiscdVal());
            }
            return true;
          })
          .filter(rec -> {
            if (StringUtils.hasText(qParam)) {
              return (rec.getNumber1()!=null && rec.getNumber1().contains(qParam))
                  || (rec.getNumber2()!=null && rec.getNumber2().contains(qParam))
                  || (rec.getCallStatus()!=null && rec.getCallStatus().contains(qParam));
            }
            return true;
          })
          .filter(rec -> {
            if (start != null || end != null) {
              LocalDateTime callTime = rec.getCallStartDateTime() != null
                  ? LocalDateTime.parse(rec.getCallStartDateTime(),
                  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                  : null;
              if (callTime != null) {
                if (start != null && callTime.isBefore(start)) return false;
                if (end   != null && callTime.isAfter(end))   return false;
              }
            }
            return true;
          })
          .toList();

      // 수동 페이징
      int total = filtered.size();
      int fromIndex = page * size;
      int toIndex   = Math.min(fromIndex + size, total);
      List<TrecordDto> pageContent = (fromIndex < total)
          ? filtered.subList(fromIndex, toIndex)
          : Collections.emptyList();
      paged = new PageImpl<>(pageContent, reqPage, total);

      // ▼▼▼ [수정] 건수 계산 로직: 필터 값에 따라 분기 처리 ▼▼▼
      if ("ALL".equalsIgnoreCase(directionParam)) {
        inboundCount  = filtered.stream().filter(r -> "수신".equals(r.getIoDiscdVal())).count();
        outboundCount = filtered.stream().filter(r -> "발신".equals(r.getIoDiscdVal())).count();
      } else if ("IN".equalsIgnoreCase(directionParam)) {
        inboundCount = total;
        outboundCount = 0;
      } else { // "OUT"
        inboundCount = 0;
        outboundCount = total;
      }
      // ▲▲▲ [수정] 건수 계산 로직 ▲▲▲

    }
    // ③ 통합된 기본 검색 로직
    else {
      String lvl = me.getUserLevel();
      List<String> numsForLevel1 = null;
      List<String> numsForLevel2 = null;

      // 데이터 조회를 위한 사용자별 내선번호 목록 미리 준비
      if ("1".equals(lvl)) {
        numsForLevel1 = memberSvc.listUsersInBranch(me.getBranchSeq(), PageRequest.of(0, Integer.MAX_VALUE))
            .getContent().stream()
            .map(Info::getNumber).filter(StringUtils::hasText)
            .map(this::normalizeToFourDigit).filter(Objects::nonNull)
            .toList();
      } else if ("2".equals(lvl)) {
        numsForLevel2 = getAccessibleNumbers(me.getUserId());
      }

      // 권한별 데이터 조회
      if ("0".equals(lvl)) {
        paged = recordSvc.search(null, null, directionParam, searchNumberKind, searchQuery, start, end, reqPage);
      } else if ("1".equals(lvl)) {
        if (numsForLevel1.isEmpty() && !StringUtils.hasText(searchQuery)) {
          paged = Page.empty(reqPage);
        } else {
          paged = recordSvc.searchByMixedNumbers(numsForLevel1, directionParam, searchNumberKind, searchQuery, start, end, reqPage);
        }
      } else if ("2".equals(lvl)) {
        if (numsForLevel2.isEmpty() && !StringUtils.hasText(searchQuery)) {
          paged = Page.empty(reqPage);
        } else {
          paged = recordSvc.searchByMixedNumbers(numsForLevel2, directionParam, searchNumberKind, searchQuery, start, end, reqPage);
        }
      } else {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
      }

      // ▼▼▼ [수정] 건수 계산 로직: 필터 값에 따라 분기 처리 ▼▼▼
      if ("ALL".equalsIgnoreCase(directionParam)) {
        // '전체' 보기일 경우, 수신/발신 건수를 각각 따로 조회
        if ("0".equals(lvl)) {
          inboundCount = recordSvc.search(null, null, "IN", searchNumberKind, searchQuery, start, end, one).getTotalElements();
          outboundCount = recordSvc.search(null, null, "OUT", searchNumberKind, searchQuery, start, end, one).getTotalElements();
        } else { // lvl 1 and 2
          List<String> nums = "1".equals(lvl) ? numsForLevel1 : numsForLevel2;
          if ((nums == null || nums.isEmpty()) && !StringUtils.hasText(searchQuery)) {
            inboundCount = 0;
            outboundCount = 0;
          } else {
            inboundCount = recordSvc.searchByMixedNumbers(nums, "IN", searchNumberKind, searchQuery, start, end, one).getTotalElements();
            outboundCount = recordSvc.searchByMixedNumbers(nums, "OUT", searchNumberKind, searchQuery, start, end, one).getTotalElements();
          }
        }
      } else if ("IN".equalsIgnoreCase(directionParam)) {
        // '수신'으로 필터링된 경우
        inboundCount = paged.getTotalElements();
        outboundCount = 0;
      } else { // "OUT" 또는 기타
        // '발신'으로 필터링된 경우
        inboundCount = 0;
        outboundCount = paged.getTotalElements();
      }
      // ▲▲▲ [수정] 건수 계산 로직 ▲▲▲
    }

    // ④ 후처리: lineId, masking, listenAuth 등 공통 로직
    String userLevel = me.getUserLevel();
    paged.getContent().forEach(rec -> {
      // 내선번호 형식 변환
      if (rec.getNumber1() != null) rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));

      // 전화번호 마스킹 (사용자 설정에 따라)
      if (me.getMaskFlag() != null && me.getMaskFlag() == 0) {
        rec.maskNumber2();
      }

      // lineId 설정
      if (rec.getLineId() == null
          && rec.getNumber1() != null
          && rec.getNumber1().length() == 4) {
        trecordTelListRepository.findByCallNum(rec.getNumber1())
            .map(TrecordTelListEntity::getId)
            .ifPresent(rec::setLineId);
      }

      // 청취 권한 설정
      boolean canListen = "0".equals(userLevel)
          || "1".equals(userLevel)
          || ("2".equals(userLevel) && hasPermissionForNumber(me.getUserId(), rec.getNumber1(), 3));
      try {
        Field f = TrecordDto.class.getDeclaredField("listenAuth");
        f.setAccessible(true);
        f.set(rec, canListen ? "가능" : "불가능");
      } catch (Exception ignore) {}
    });

    // ⑤ 응답 바디 구성
    Map<String,Object> body = new LinkedHashMap<>();
    body.put("content",          paged.getContent());
    body.put("totalElements",    paged.getTotalElements());
    body.put("totalPages",       paged.getTotalPages());
    body.put("size",             paged.getSize());
    body.put("number",           paged.getNumber());
    body.put("numberOfElements", paged.getNumberOfElements());
    body.put("inboundCount",     inboundCount);
    body.put("outboundCount",    outboundCount);
    body.put("empty",            paged.isEmpty());
    body.put("first",            paged.isFirst());
    body.put("last",             paged.isLast());
    body.put("pageable",         paged.getPageable());
    body.put("sort",             paged.getSort());

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
      @RequestParam(value = "audioFiles", required = false) String audioFilesCsv,
      @RequestParam(name = "q",           required = false) String q,
      @RequestParam(name = "start",       required = false) String startStr,
      @RequestParam(name = "end",         required = false) String endStr,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size
  ) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = (startStr != null && !startStr.isEmpty()) ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end   = (endStr   != null && !endStr.isEmpty()) ? LocalDateTime.parse(endStr,   fmt) : null;

    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    Pageable pr = PageRequest.of(page, size);
    Page<TrecordDto> paged;

    // ✅ audioFilesCsv 우선 처리
    if (audioFilesCsv != null && !audioFilesCsv.isBlank()) {
      List<String> audioFileDirs = Arrays.stream(audioFilesCsv.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .toList();

      paged = recordSvc.searchByAudioFileNames(audioFileDirs, pr);

      // 후처리
      paged.getContent().forEach(rec -> {
        if (rec.getNumber1() != null) rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
        if (rec.getNumber2() != null) rec.setNumber2(convertToExtensionDisplay(rec.getNumber2()));
      });
      if (me.getMaskFlag() != null && me.getMaskFlag() == 0) {
        paged.getContent().forEach(TrecordDto::maskNumber2);
      }

      return ResponseEntity.ok(paged);
    }

    // ✅ numberKind 보정
    if (!StringUtils.hasText(q) && "PHONE".equalsIgnoreCase(numberKind)) {
      numberKind = "ALL";
    }
    if (StringUtils.hasText(q)) {
      numberKind = "ALL";
    }

    // ✅ 권한별 검색
    if ("0".equals(lvl) || "1".equals(lvl)) {
      if (numbersCsv != null && !numbersCsv.isBlank()) {
        List<String> nums = Arrays.stream(numbersCsv.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
        paged = recordSvc.searchByMixedNumbers(nums, direction, numberKind, q, start, end, pr);
      } else if ((number1 != null && !number1.isBlank()) || (number2 != null && !number2.isBlank())) {
        paged = recordSvc.search(number1, number2, direction, numberKind, q, start, end, pr);
      } else {
        paged = recordSvc.search(null, null, direction, numberKind, q, start, end, pr);
      }
    }

    else if ("2".equals(lvl)) {
      List<String> accessible = getAccessibleNumbers(me.getUserId());
      if (accessible.isEmpty()) {
        paged = Page.empty(pr);
      } else {
        List<String> nums = (numbersCsv != null && !numbersCsv.isBlank())
            ? Arrays.stream(numbersCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .filter(accessible::contains)
            .toList()
            : accessible;

        paged = nums.isEmpty() ? Page.empty(pr) : recordSvc.searchByMixedNumbers(nums, pr);
      }
    }

    else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    // ✅ 후처리
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
  @LogActivity(
      type     = "record",
      activity = "청취",
      contents = "녹취Seq=#{#id}, offset=#{#return.body.position}, length=#{#return.body.count}"
  )

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
  @LogActivity(
      type     = "record",
      activity = "다운로드",
      contents = "녹취Seq=#{#id}, file=#{#return.body.filename}"
  )

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
