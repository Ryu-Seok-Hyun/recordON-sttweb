package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.exception.ResourceNotFoundException;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import com.sttweb.sttweb.entity.TrecordTelListEntity;
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

  // ── 1) 헤더에서 토큰 파싱 & 사용자 조회 ──
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
    if (rec.getNumber1() != null) rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
    if (me.getMaskFlag() != null && me.getMaskFlag() == 0) rec.maskNumber2();

    // 기존 listenAuth 세팅 로직
    boolean canListen = "0".equals(me.getUserLevel())
        || "1".equals(me.getUserLevel())
        || ("2".equals(me.getUserLevel()) && hasPermissionForNumber(me.getUserId(), rec.getNumber1(), 3));
    try {
      Field f = TrecordDto.class.getDeclaredField("listenAuth");
      f.setAccessible(true);
      f.set(rec, canListen ? "가능" : "불가능");
    } catch (Exception ignored) {}


       // ▶▶ 바로 path(절대경로)만 내려줍니다.
         rec.setListenUrl("/records/"  + rec.getRecordSeq() + "/listen");
       rec.setDownloadUrl("/records/" + rec.getRecordSeq() + "/download");
    // ────────────────────────────────────────
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

  /** ── 스트리밍(청취) ── */
  @LogActivity(type = "record", activity = "청취", contents = "녹취Seq=#{#id}")
  @GetMapping("/{id}/listen")
  public ResponseEntity<StreamingResponseBody> streamAudio(
      HttpServletRequest request, @PathVariable Integer id) throws IOException {
    Info me = requireLogin(request);
    Resource audio = recordSvc.getFile(id);
    if (audio == null || !audio.exists() || !audio.isReadable()) {
      throw new ResourceNotFoundException("파일을 찾을 수 없습니다: " + id);
    }
    MediaType mediaType = MediaTypeFactory.getMediaType(audio)
        .orElse(MediaType.APPLICATION_OCTET_STREAM);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(mediaType);
    headers.setContentLength(audio.contentLength());

    StreamingResponseBody body = os -> {
      try (var is = audio.getInputStream()) {
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) != -1) {
          os.write(buf, 0, len);
        }
      }
    };
    return new ResponseEntity<>(body, headers, HttpStatus.OK);
  }


  /** ── 다운로드 ── */
  @LogActivity(type = "record", activity = "다운로드", contents = "녹취Seq=#{#id}")
  @GetMapping("/{id}/download")
  public void downloadById(HttpServletRequest request,
      HttpServletResponse response,
      @PathVariable Integer id) throws IOException {
    Info me = requireLogin(request);
    // 만약 권한 체크가 필요하면 hasPermissionForNumber(...) 호출
    serveLocalFile(id, response, true);
  }

  /** 공통: 로컬 파일 서빙 */
  private void serveLocalFile(Integer id,
      HttpServletResponse response,
      boolean isDownload) throws IOException {
    Resource file = recordSvc.getFile(id);
    if (file == null || !file.exists() || !file.isReadable()) {
      response.sendError(HttpStatus.NOT_FOUND.value(), "파일을 찾을 수 없습니다: " + id);
      return;
    }
    MediaType mediaType = MediaTypeFactory.getMediaType(file)
        .orElse(MediaType.APPLICATION_OCTET_STREAM);
    response.setContentType(mediaType.toString());
    response.setContentLengthLong(file.contentLength());
    if (isDownload) {
      String fn = UriUtils.encode(file.getFilename(), StandardCharsets.UTF_8);
      response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
          "attachment; filename=\"" + fn + "\"");
    }
    StreamUtils.copy(file.getInputStream(), response.getOutputStream());
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
