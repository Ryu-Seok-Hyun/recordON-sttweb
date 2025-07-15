package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.crypto.CryptoProperties;
import com.sttweb.sttweb.crypto.CryptoUtil;
import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.exception.ResourceNotFoundException;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.service.RecOnDataService;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import com.sttweb.sttweb.entity.TrecordTelListEntity;
import jakarta.servlet.ServletOutputStream;
import java.io.InputStream;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;


@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private static final Logger log = LoggerFactory.getLogger(TrecordController.class);

  private final TrecordService recordSvc;
  private final TmemberService memberSvc;
  private final JwtTokenProvider jwtTokenProvider;
  private final UserPermissionRepository userPermRepo;
  private final TrecordTelListRepository trecordTelListRepository;
  private final TbranchService branchSvc;
  private final RestTemplate restTemplate;
  private final CryptoProperties cryptoProps;
  private final RecOnDataService recOnDataService;


  // ── 1) 헤더에서 토큰 파싱 & 사용자 조회 ──
  private Info requireLogin(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
    }
    String token = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    }

    String userId = jwtTokenProvider.getUserId(token);
    log.debug("[requireLogin] 추출된 userId: {}", userId);

    Info info = memberSvc.getMyInfoByUserId(userId);
    if (info == null) {
      log.warn("[requireLogin] userId로 사용자 정보를 찾을 수 없음: {}", userId);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다.");
    }

    log.debug("[requireLogin] 사용자 정보 조회 성공: userId={}, memberSeq={}", info.getUserId(), info.getMemberSeq());
    return info;
  }

  // ── 2) 헤더 또는 쿠키에서 토큰 파싱 & 사용자 조회 ──
  private Info requireLogin(HttpServletRequest req) {
    String authHeader = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (authHeader == null) {
      Cookie[] cookies = req.getCookies();
      if (cookies != null) {
        for (Cookie c : cookies) {
          if ("Authorization".equals(c.getName())) {
            authHeader = "Bearer " + c.getValue();
            break;
          }
        }
      }
    }
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
    }
    String token = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    }
    return memberSvc.getMyInfoByUserId(jwtTokenProvider.getUserId(token));
  }


  // ── 3) 내선번호 4자리 정규화 ──
  private String normalizeToFourDigit(String n) {
    if (n == null) return null;
    String d = n.replaceAll("[^0-9]", "");
    if (d.length() == 3) return "0" + d;
    if (d.length() > 4) return d.substring(d.length() - 4);
    return d.length() == 4 ? d : null;
  }

  // ── 4) 내선번호 확장 목록 생성 ──
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

  // ── 5) 내선번호 화면용 표시 ──
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

  // ── 6) 권한받은 내선 목록 조회 ──
  private List<String> getAccessibleNumbers(String userId) {
    Set<String> numbers = new LinkedHashSet<>();
    Info me = memberSvc.getMyInfoByUserId(userId);

    if (me.getNumber() != null && !me.getNumber().trim().isEmpty()) {
      String n = normalizeToFourDigit(me.getNumber());
      if (n != null) numbers.add(n);
    }

    Map<Integer, String> lineMap = trecordTelListRepository.findAll().stream()
        .collect(Collectors.toMap(TrecordTelListEntity::getId, TrecordTelListEntity::getCallNum));

    for (UserPermission perm : userPermRepo.findByMemberSeq(me.getMemberSeq())) {
      if (perm.getPermLevel() >= 2 && perm.getLineId() != null) {
        String ext = lineMap.get(perm.getLineId());
        String n = normalizeToFourDigit(ext);
        if (n != null) numbers.add(n);
      }
    }
    return new ArrayList<>(numbers);
  }

  // ── 7) 번호별 권한 체크 ──
  private boolean hasPermissionForNumber(String userId, String number, int reqLevel) {
    if (number == null) return false;
    List<String> target = makeExtensions(number);
    Info me = memberSvc.getMyInfoByUserId(userId);

    if (me.getNumber() != null) {
      String myNormalizedNumber = normalizeToFourDigit(me.getNumber());
      if (myNormalizedNumber != null && target.contains(myNormalizedNumber)) {
        return true;
      }
    }

    Map<Integer, String> lineMap = trecordTelListRepository.findAll().stream()
        .collect(Collectors.toMap(TrecordTelListEntity::getId, TrecordTelListEntity::getCallNum));

    for (UserPermission perm : userPermRepo.findByMemberSeq(me.getMemberSeq())) {
      if (perm.getPermLevel() >= reqLevel && perm.getLineId() != null) {
        String grantedExt = lineMap.get(perm.getLineId());
        if (grantedExt != null) {
          List<String> grantedExtensions = makeExtensions(grantedExt);
          for (String ext : grantedExtensions) {
            if (target.contains(ext)) return true;
          }
        }
      }
    }
    return false;
  }

  // ── 8) 현재 서버가 해당 지점 서버인지 비교 ──
  // [최종 수정] ── 8) Apache Reverse Proxy 환경을 고려하여 현재 서버 식별 ──
  private boolean isCurrentServerBranch(TbranchEntity branch, HttpServletRequest req) {
    // Apache Proxy가 넘겨준 원래 요청 Host(X-Forwarded-Host)를 우선적으로 확인
    String forwardedHost = req.getHeader("X-Forwarded-Host");
    String effectiveHost = StringUtils.hasText(forwardedHost) ? forwardedHost : req.getHeader(HttpHeaders.HOST);

    if (effectiveHost == null) {
      effectiveHost = req.getServerName() + ":" + req.getServerPort();
    }

    String ip = effectiveHost.contains(":") ? effectiveHost.split(":")[0] : effectiveHost;
    String port = effectiveHost.contains(":") ? effectiveHost.substring(effectiveHost.indexOf(":") + 1) : String.valueOf(req.getServerPort());

    log.debug("isCurrentServerBranch check: branchIP={}, branchPort={}, requestIP={}, requestPort={}",
        branch.getPIp(), branch.getPPort(), ip, port);

    return branch.getPIp().equals(ip) && branch.getPPort().equals(port);
  }


  // ── 9) 전체 녹취 리스트 ──
  @LogActivity(type = "record", activity = "조회", contents = "전체 녹취 조회")
  @GetMapping
  public ResponseEntity<Map<String, Object>> listAll(
      @RequestParam(name = "number", required = false) String numberParam,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "audioFiles", required = false) String audioFilesCsv,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size,
      @RequestParam(name = "direction", defaultValue = "ALL") String directionParam,
      @RequestParam(name = "numberKind", defaultValue = "ALL") String numberKindParam,
      @RequestParam(name = "q", required = false) String qParam,
      @RequestParam(name = "start", required = false) String startStr,
      @RequestParam(name = "end", required = false) String endStr
  ) {
    Info me = requireLogin(authHeader);
    Pageable reqPage = PageRequest.of(page, size);

    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = StringUtils.hasText(startStr) ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end = StringUtils.hasText(endStr) ? LocalDateTime.parse(endStr, fmt) : null;

    if (StringUtils.hasText(numberParam)) {
      Page<TrecordDto> paged = recordSvc.searchByPhoneEnding(numberParam, reqPage);
      paged.getContent().forEach(rec -> postProcessRecordDto(rec, me));
      long inboundCount = paged.stream().filter(r -> "수신".equals(r.getIoDiscdVal())).count();
      long outboundCount = paged.stream().filter(r -> "발신".equals(r.getIoDiscdVal())).count();
      return ResponseEntity.ok(buildPaginatedResponse(paged, inboundCount, outboundCount));
    }

    String searchQuery = StringUtils.hasText(numberParam) ? numberParam : qParam;
    String searchNumberKind = StringUtils.hasText(numberParam) ? numberKindParam : (StringUtils.hasText(qParam) ? "ALL" : "ALL");

    Page<TrecordDto> paged;
    long inboundCount = 0;
    long outboundCount = 0;
    Pageable one = PageRequest.of(0, 1);

    if (StringUtils.hasText(audioFilesCsv)) {
      List<String> audioFiles = Arrays.stream(audioFilesCsv.split(","))
          .map(String::trim).filter(s -> !s.isEmpty()).toList();

      List<TrecordDto> allResults = recordSvc.searchByAudioFileNames(audioFiles, Pageable.unpaged()).getContent();

      List<TrecordDto> filtered = allResults.stream()
          .filter(rec -> filterByDirection(rec, directionParam))
          .filter(rec -> filterByQuery(rec, qParam))
          .filter(rec -> filterByDate(rec, start, end))
          .toList();

      paged = new PageImpl<>(paginateList(filtered, page, size), reqPage, filtered.size());

      if ("ALL".equalsIgnoreCase(directionParam)) {
        inboundCount = filtered.stream().filter(r -> "수신".equals(r.getIoDiscdVal())).count();
        outboundCount = filtered.stream().filter(r -> "발신".equals(r.getIoDiscdVal())).count();
      } else if ("IN".equalsIgnoreCase(directionParam)) {
        inboundCount = filtered.size();
      } else {
        outboundCount = filtered.size();
      }
    } else {
      String lvl = me.getUserLevel();

      if ("0".equals(lvl)) {
        paged = recordSvc.search(null, null, directionParam, searchNumberKind, searchQuery, start, end, reqPage);
        if ("ALL".equalsIgnoreCase(directionParam)) {
          inboundCount = recordSvc.search(null, null, "IN", searchNumberKind, searchQuery, start, end, one).getTotalElements();
          outboundCount = recordSvc.search(null, null, "OUT", searchNumberKind, searchQuery, start, end, one).getTotalElements();
        }
      } else {
        List<String> accessibleNumbers = ("1".equals(lvl))
            ? memberSvc.listUsersInBranch(me.getBranchSeq(), PageRequest.of(0, Integer.MAX_VALUE))
            .getContent().stream()
            .map(Info::getNumber).filter(StringUtils::hasText)
            .map(this::normalizeToFourDigit).filter(Objects::nonNull)
            .toList()
            : getAccessibleNumbers(me.getUserId());

        if (accessibleNumbers.isEmpty() && !StringUtils.hasText(searchQuery)) {
          paged = Page.empty(reqPage);
        } else {
          paged = recordSvc.searchByMixedNumbers(accessibleNumbers, directionParam, searchNumberKind, searchQuery, start, end, reqPage);
        }

        if ("ALL".equalsIgnoreCase(directionParam)) {
          if (accessibleNumbers.isEmpty() && !StringUtils.hasText(searchQuery)) {
            inboundCount = 0;
            outboundCount = 0;
          } else {
            inboundCount = recordSvc.searchByMixedNumbers(accessibleNumbers, "IN", searchNumberKind, searchQuery, start, end, one).getTotalElements();
            outboundCount = recordSvc.searchByMixedNumbers(accessibleNumbers, "OUT", searchNumberKind, searchQuery, start, end, one).getTotalElements();
          }
        }
      }

      if (!"ALL".equalsIgnoreCase(directionParam)) {
        if ("IN".equalsIgnoreCase(directionParam)) {
          inboundCount = paged.getTotalElements();
        } else { // OUT
          outboundCount = paged.getTotalElements();
        }
      }
    }

    paged.getContent().forEach(rec -> postProcessRecordDto(rec, me));
    return ResponseEntity.ok(buildPaginatedResponse(paged, inboundCount, outboundCount));
  }

  private void postProcessRecordDto(TrecordDto rec, Info me) {
    if (rec.getNumber1() != null)
      rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
    if (me.getMaskFlag() != null && me.getMaskFlag() == 0)
      rec.maskNumber2();

    // 기존 listenAuth 세팅 로직
    boolean canListen = "0".equals(me.getUserLevel())
        || "1".equals(me.getUserLevel())
        || ("2".equals(me.getUserLevel()) && hasPermissionForNumber(me.getUserId(),
        rec.getNumber1(), 3));
    try {
      Field f = TrecordDto.class.getDeclaredField("listenAuth");
      f.setAccessible(true);
      f.set(rec, canListen ? "가능" : "불가능");
    } catch (Exception ignored) {
    }

    // ▶▶ 바로 path(절대경로)만 내려줍니다.
    rec.setListenUrl("/api/records/" + rec.getRecordSeq() + "/listen");
    rec.setDownloadUrl("/api/records/" + rec.getRecordSeq() + "/download");
    // ────────────────────────────────────────

    String csdt = rec.getCallStartDateTime();
    if (csdt != null) {
      String dateDir = csdt.substring(0,10).replace("-", "");
      // "../20250619/…". 앞의 "../" 제거
      String afd = rec.getAudioFileDir()
          .replace("\\","/")
          .replaceFirst("^\\.\\./+","");
      String fname = afd.substring(afd.lastIndexOf('/') + 1);

      log.debug("[JSON 체크] dateDir={}, fname={}", dateDir, fname);
      boolean exists = recOnDataService.isJsonGenerated(dateDir, fname);
      rec.setJsonExists(exists);
    } else {
      rec.setJsonExists(false);
    }
  }


  private Map<String, Object> buildPaginatedResponse(Page<TrecordDto> paged, long inboundCount, long outboundCount) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("content", paged.getContent());
    body.put("totalElements", paged.getTotalElements());
    body.put("totalPages", paged.getTotalPages());
    body.put("size", paged.getSize());
    body.put("number", paged.getNumber());
    body.put("numberOfElements", paged.getNumberOfElements());
    body.put("inboundCount", inboundCount);
    body.put("outboundCount", outboundCount);
    body.put("empty", paged.isEmpty());
    body.put("first", paged.isFirst());
    body.put("last", paged.isLast());
    body.put("pageable", paged.getPageable());
    body.put("sort", paged.getSort());
    return body;
  }

  private boolean filterByDirection(TrecordDto rec, String directionParam) {
    if ("ALL".equalsIgnoreCase(directionParam)) return true;
    if ("IN".equalsIgnoreCase(directionParam)) return "수신".equals(rec.getIoDiscdVal());
    if ("OUT".equalsIgnoreCase(directionParam)) return "발신".equals(rec.getIoDiscdVal());
    return false;
  }

  private boolean filterByQuery(TrecordDto rec, String qParam) {
    if (!StringUtils.hasText(qParam)) return true;
    return (rec.getNumber1() != null && rec.getNumber1().contains(qParam))
        || (rec.getNumber2() != null && rec.getNumber2().contains(qParam))
        || (rec.getCallStatus() != null && rec.getCallStatus().contains(qParam));
  }

  private boolean filterByDate(TrecordDto rec, LocalDateTime start, LocalDateTime end) {
    if (start == null && end == null) return true;
    LocalDateTime callTime = rec.getCallStartDateTime() != null
        ? LocalDateTime.parse(rec.getCallStartDateTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        : null;
    if (callTime == null) return false;
    if (start != null && callTime.isBefore(start)) return false;
    if (end != null && callTime.isAfter(end)) return false;
    return true;
  }

  private <T> List<T> paginateList(List<T> list, int page, int size) {
    int fromIndex = page * size;
    int toIndex = Math.min(fromIndex + size, list.size());
    if (fromIndex >= list.size()) {
      return Collections.emptyList();
    }
    return list.subList(fromIndex, toIndex);
  }


  // ── 10) 번호로 녹취 검색 ──
  @LogActivity(type = "record", activity = "조회", contents = "번호 검색")
  @GetMapping("/search")
  public ResponseEntity<Page<TrecordDto>> searchByNumbers(
      @RequestParam(value = "numbers", required = false) String numbersCsv,
      @RequestParam(value = "number1", required = false) String number1,
      @RequestParam(value = "number2", required = false) String number2,
      @RequestParam(name = "direction", defaultValue = "ALL") String direction,
      @RequestParam(name = "numberKind", defaultValue = "ALL") String numberKind,
      @RequestParam(value = "audioFiles", required = false) String audioFilesCsv,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "start", required = false) String startStr,
      @RequestParam(name = "end", required = false) String endStr,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size
  ) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = StringUtils.hasText(startStr) ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end = StringUtils.hasText(endStr) ? LocalDateTime.parse(endStr, fmt) : null;

    Info me = requireLogin(authHeader);
    Pageable pr = PageRequest.of(page, size);
    Page<TrecordDto> paged;

    if (StringUtils.hasText(audioFilesCsv)) {
      List<String> dirs = Arrays.stream(audioFilesCsv.split(","))
          .map(String::trim).filter(s -> !s.isEmpty()).toList();
      paged = recordSvc.searchByAudioFileNames(dirs, pr);
    } else {
      String lvl = me.getUserLevel();
      if ("0".equals(lvl) || "1".equals(lvl)) {
        if (StringUtils.hasText(numbersCsv)) {
          List<String> nums = Arrays.stream(numbersCsv.split(","))
              .map(String::trim).filter(s -> !s.isEmpty()).toList();
          paged = recordSvc.searchByMixedNumbers(nums, direction, numberKind, q, start, end, pr);
        } else {
          paged = recordSvc.search(number1, number2, direction, numberKind, q, start, end, pr);
        }
      } else if ("2".equals(lvl)) {
        List<String> accessible = getAccessibleNumbers(me.getUserId());
        List<String> searchNumbers;
        if (StringUtils.hasText(numbersCsv)) {
          searchNumbers = Arrays.stream(numbersCsv.split(","))
              .map(String::trim).filter(s -> !s.isEmpty())
              .filter(accessible::contains).toList();
        } else {
          searchNumbers = accessible;
        }
        if (searchNumbers.isEmpty()) {
          paged = Page.empty(pr);
        } else {
          paged = recordSvc.searchByMixedNumbers(searchNumbers, direction, numberKind, q, start, end, pr);
        }
      } else {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
      }
    }

    paged.getContent().forEach(rec -> postProcessRecordDto(rec, me));
    return ResponseEntity.ok(paged);
  }

  // ── 11) 단건 녹취 조회 ──
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
    if (me.getMaskFlag() != null && me.getMaskFlag() == 0) dto.maskNumber2();

    return ResponseEntity.ok(dto);
  }







  /**
   * `.aes` 확장자는 복호화 스트림을, 그 외는 원본 스트림을 그대로 반환합니다.
   */
  private StreamingResponseBody buildStream(Resource audio) throws Exception {
    InputStream raw = audio.getInputStream();
    InputStream in = audio.getFilename().toLowerCase().endsWith(".aes")
        ? CryptoUtil.decryptingStream(raw, cryptoProps.getSecretKey())
        : raw;

    return out -> {
      try (InputStream is = in) {
        StreamUtils.copy(is, out);
      }
    };
  }



  /**
   * ── 녹취 스트리밍 (프록시 기능 추가) ──
   * 요청된 녹취 파일이 현재 서버에 있으면 직접 스트리밍하고,
   * 다른 지점 서버에 있으면 해당 서버로 요청을 프록시하여 결과를 스트리밍합니다.
   */
  @LogActivity(type = "record", activity = "청취", contents = "녹취Seq=#{#id}")
  @GetMapping("/{id}/listen")
  public ResponseEntity<StreamingResponseBody> listen(
      HttpServletRequest request,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @PathVariable("id") Integer id
  ) throws Exception {
    // 1) 인증 → Info 객체 리턴받도록 변경
    Info me = requireLogin(authHeader);

    // 2) 청취 권한 확인 (permLevel >= 3)
    TrecordDto recDto = recordSvc.findById(id);
    boolean canListen =
        hasPermissionForNumber(me.getUserId(), recDto.getNumber1(), 3)
            || hasPermissionForNumber(me.getUserId(), recDto.getNumber2(), 3)
            || "0".equals(me.getUserLevel())  // super-admin
            || "1".equals(me.getUserLevel()); // branch-admin
    if (!canListen) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "녹취 재생 권한이 없습니다.");
    }


    // 2) 리소스(오디오 파일) 로드
    Resource audio = recordSvc.getFile(id);
    if (audio == null || !audio.exists() || !audio.isReadable()) {
      log.debug("[녹취 청취] 파일을 찾을 수 없습니다. 녹취 ID={}", id);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다: " + id);
    }

    // 3) MediaType & Range 확인
    long contentLength = audio.contentLength();
    MediaType mediaType = MediaTypeFactory.getMediaType(audio)
        .orElse(MediaType.APPLICATION_OCTET_STREAM);
    String rangeHeader = request.getHeader(HttpHeaders.RANGE);
    List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);

    if (!ranges.isEmpty()) {
      HttpRange range = ranges.get(0);
      long start = range.getRangeStart(contentLength);
      long end = range.getRangeEnd(contentLength);
      long rangeLength = end - start + 1;

      InputStream rangeStream = audio.getInputStream();
      rangeStream.skip(start);

      StreamingResponseBody rangeBody = out -> {
        byte[] buffer = new byte[8192];
        long remaining = rangeLength;
        int bytesRead;
        while (remaining > 0 && (bytesRead = rangeStream.read(buffer, 0,
            (int) Math.min(buffer.length, remaining))) != -1) {
          out.write(buffer, 0, bytesRead);
          out.flush();
          remaining -= bytesRead;
        }
        rangeStream.close();
      };

      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
          .contentType(mediaType)
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .header(HttpHeaders.CONTENT_RANGE,
              String.format("bytes %d-%d/%d", start, end, contentLength))
          .contentLength(rangeLength)
          .body(rangeBody);
    }

    // ▶ 전체 스트리밍 (aes 복호화 포함)
    StreamingResponseBody fullBody = buildStream(audio);
    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .contentLength(contentLength)
        .body(fullBody);
  }


  // ─────────────────────────────────────────────
  @GetMapping("/{id}/test-listen")
  public ResponseEntity<String> testListen(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    requireLogin(authHeader);

    try {
      Resource audio = recordSvc.getFile(id);
      if (audio == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("파일 객체가 null입니다.");
      }

      if (!audio.exists()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("파일이 존재하지 않습니다.");
      }

      if (!audio.isReadable()) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("파일이 존재하지만 읽을 수 없습니다.");
      }

      long length = audio.contentLength();
      String info = String.format("파일 확인 완료\n크기: %d bytes\n이름: %s",
          length,
          audio.getFilename());
      return ResponseEntity.ok(info);

    } catch (IOException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("파일 확인 중 오류 발생: " + e.getMessage());
    }
  }


  // ─────────────────────────────────────────────
  /**
   * ── 다운로드 (프록시 기능 추가) ──
   */
  @LogActivity(type = "record", activity = "다운로드", contents = "녹취Seq=#{#id}")
  @GetMapping("/{id}/download")
  public void downloadById(
      HttpServletRequest request,
      HttpServletResponse response,
      @PathVariable Integer id) throws Exception {

    // 1) 인증
    Info me = requireLogin(request);

    // 2) 다운로드 권한 확인 (permLevel >= 4)
    TrecordDto recordDto = recordSvc.findById(id);
    boolean canDownload =
        hasPermissionForNumber(me.getUserId(), recordDto.getNumber1(), 4)
            || hasPermissionForNumber(me.getUserId(), recordDto.getNumber2(), 4)
            || "0".equals(me.getUserLevel())  // super-admin
            || "1".equals(me.getUserLevel()); // branch-admin
    if (!canDownload) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다운로드 권한이 없습니다.");
    }


    if (recordDto.getBranchSeq() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "녹취 파일의 소속 지점 정보를 찾을 수 없습니다.");
    }

    TbranchEntity branch = branchSvc.findEntityBySeq(recordDto.getBranchSeq());
    if (isCurrentServerBranch(branch, request)) {
      log.debug("녹취 파일(ID: {})을 로컬에서 다운로드합니다.", id);
      serveLocalFile(id, response);
    } else {
      log.debug("녹취 파일(ID: {})을 원격 지점({})으로 프록시 다운로드 요청합니다.", id, branch.getPIp());
      proxyFileRequest(branch, id, "download", request, response);
    }
  }

  /**
   * 로컬 저장된 녹취(.wav 또는 .aes)를 buildStream 으로 읽어서 다운로드합니다.
   */
  private void serveLocalFile(Integer id,
      HttpServletResponse response) throws Exception {
    Resource file = recordSvc.getFile(id);
    if (file == null || !file.exists() || !file.isReadable()) {
      response.sendError(HttpStatus.NOT_FOUND.value(), "파일을 찾을 수 없습니다: " + id);
      return;
    }

    MediaType mediaType = MediaTypeFactory.getMediaType(file)
        .orElse(MediaType.APPLICATION_OCTET_STREAM);
    response.setContentType(mediaType.toString());
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" +
            UriUtils.encode(file.getFilename(), StandardCharsets.UTF_8) + "\"");
    response.setContentLengthLong(file.contentLength());

    // buildStream 으로 복호화 포함 스트리밍
    StreamingResponseBody body = buildStream(file);
    try (ServletOutputStream out = response.getOutputStream()) {
      body.writeTo(out);
    }
  }

  /**
   * 신규 추가: 원격 지점 서버로 파일 요청을 중계(프록시)하는 헬퍼 메소드
   */
  private void proxyFileRequest(TbranchEntity targetBranch, Integer recordId, String action,
      HttpServletRequest request, HttpServletResponse response) {

    // 1. 목표 URL 생성 (예: http://192.168.0.10:39090/api/records/123/listen)
    String targetUrl = String.format("http://%s:%s/api/records/%d/%s",
        targetBranch.getPIp(), targetBranch.getPPort(), recordId, action);

    try {
      // 2. RestTemplate을 사용하여 원격 서버에 요청 실행
      restTemplate.execute(
          URI.create(targetUrl),
          HttpMethod.GET,
          clientRequest -> {
            // 3. 원본 요청의 헤더를 프록시 요청에 복사 (특히 Authorization 헤더가 중요)
            Collections.list(request.getHeaderNames()).forEach(headerName -> {
              // host, content-length 등 일부 헤더는 RestTemplate이 자동으로 관리하므로 제외
              if (!headerName.equalsIgnoreCase(HttpHeaders.HOST) &&
                  !headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                clientRequest.getHeaders().add(headerName, request.getHeader(headerName));
              }
            });
          },
          clientResponse -> {
            // 4. 원격 서버의 응답을 클라이언트의 응답으로 복사
            response.setStatus(clientResponse.getStatusCode().value());
            clientResponse.getHeaders().forEach((name, values) -> {
              // Transfer-Encoding 헤더는 복사하지 않음 (스트리밍 시 문제 발생 가능)
              if (!name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) {
                values.forEach(value -> response.addHeader(name, value));
              }
            });
            // 5. 원격 서버의 응답 본문(오디오 데이터)을 클라이언트 응답으로 스트리밍
            StreamUtils.copy(clientResponse.getBody(), response.getOutputStream());
            return null;
          }
      );
    } catch (Exception e) {
      log.error("원격 지점({})으로의 프록시 요청 실패: {}", targetUrl, e.getMessage());
      try {
        // 프록시 실패 시 클라이언트에 에러 응답 전송
        response.sendError(HttpStatus.BAD_GATEWAY.value(), "원격 서버로부터 파일을 가져오는 데 실패했습니다.");
      } catch (IOException ioException) {
        log.error("프록시 실패 에러 응답 전송 중 오류 발생", ioException);
      }
    }
  }

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
    result.getContent().forEach(r -> {
      if (r.getNumber1() != null) r.setNumber1(convertToExtensionDisplay(r.getNumber1()));
      if (r.getNumber2() != null) r.setNumber2(convertToExtensionDisplay(r.getNumber2()));
    });
    return ResponseEntity.ok(result);
  }
}
