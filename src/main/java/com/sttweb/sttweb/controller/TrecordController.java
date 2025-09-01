package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.crypto.CryptoProperties;
import com.sttweb.sttweb.crypto.CryptoUtil;
import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.entity.UserPermission;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import com.sttweb.sttweb.repository.UserPermissionRepository;
import com.sttweb.sttweb.service.RecOnDataService;
import com.sttweb.sttweb.service.SttSearchService;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import com.sttweb.sttweb.entity.TrecordTelListEntity;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private static final Logger log = LoggerFactory.getLogger(TrecordController.class);

  private final TrecordService recordSvc;
  private final TmemberService memberSvc;
  private final com.sttweb.sttweb.jwt.JwtTokenProvider jwtTokenProvider;
  private final UserPermissionRepository userPermRepo;
  private final TrecordTelListRepository trecordTelListRepository;
  private final TbranchService branchSvc;
  private final RestTemplate restTemplate;
  private final CryptoProperties cryptoProps;
  private final RecOnDataService recOnDataService;
  private final SttSearchService sttSearchService;

  private static final long TEMP_CLEANUP_THRESHOLD_MS = 60 * 60 * 1000; // 1시간

  private void cleanOldTempFiles() {
    File tmpDir = new File(System.getProperty("java.io.tmpdir"));
    File[] files = tmpDir.listFiles((dir, name) ->
        (name.startsWith("decrypted-") || name.startsWith("dec-encmp3-")) && name.endsWith(".tmp"));
    if (files == null) return;
    long cutoff = System.currentTimeMillis() - TEMP_CLEANUP_THRESHOLD_MS;
    for (File f : files) {
      try { if (f.lastModified() < cutoff) f.delete(); } catch (Exception ignore) {}
    }
  }

  private InputStream openRangeStream(Resource audio, long start) throws IOException {
    try {
      File file = audio.getFile();
      RandomAccessFile raf = new RandomAccessFile(file, "r");
      raf.seek(start);
      return new FileInputStream(raf.getFD()) {
        @Override public void close() throws IOException { super.close(); raf.close(); }
      };
    } catch (Exception e) {
      InputStream is = audio.getInputStream();
      long skipped = 0;
      while (skipped < start) {
        long s = is.skip(start - skipped);
        if (s <= 0) break;
        skipped += s;
      }
      return is;
    }
  }

  private Info requireLogin(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
    }
    String token = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    }
    String userId = jwtTokenProvider.getUserId(token);
    Info info = memberSvc.getMyInfoByUserId(userId);
    if (info == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다.");
    return info;
  }

  private Info requireLogin(HttpServletRequest req) {
    String authHeader = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (authHeader == null) {
      Cookie[] cookies = req.getCookies();
      if (cookies != null) {
        for (Cookie c : cookies) if ("Authorization".equals(c.getName())) { authHeader = "Bearer " + c.getValue(); break; }
      }
    }
    return requireLogin(authHeader);
  }

  private String normalizeToFourDigit(String n) {
    if (n == null) return null;
    String d = n.replaceAll("[^0-9]", "");
    if (d.length() == 3) return "0" + d;
    if (d.length() > 4) return d.substring(d.length() - 4);
    return d.length() == 4 ? d : null;
  }

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

  private List<String> getAccessibleNumbers(String userId) {
    Set<String> numbers = new LinkedHashSet<>();
    Info me = memberSvc.getMyInfoByUserId(userId);

    if (StringUtils.hasText(me.getNumber())) {
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

  private boolean hasPermissionForNumber(String userId, String number, int reqLevel) {
    if (number == null) return false;
    List<String> target = makeExtensions(number);
    Info me = memberSvc.getMyInfoByUserId(userId);

    if (me.getNumber() != null) {
      String mine = normalizeToFourDigit(me.getNumber());
      if (mine != null && target.contains(mine)) return true;
    }

    Map<Integer, String> lineMap = trecordTelListRepository.findAll().stream()
        .collect(Collectors.toMap(TrecordTelListEntity::getId, TrecordTelListEntity::getCallNum));

    for (UserPermission perm : userPermRepo.findByMemberSeq(me.getMemberSeq())) {
      if (perm.getPermLevel() >= reqLevel && perm.getLineId() != null) {
        String grantedExt = lineMap.get(perm.getLineId());
        if (grantedExt != null) {
          for (String ext : makeExtensions(grantedExt)) if (target.contains(ext)) return true;
        }
      }
    }
    return false;
  }

  private boolean isCurrentServerBranch(TbranchEntity branch, HttpServletRequest req) {
    String forwardedHost = req.getHeader("X-Forwarded-Host");
    String effectiveHost = StringUtils.hasText(forwardedHost) ? forwardedHost : req.getHeader(HttpHeaders.HOST);
    if (effectiveHost == null) effectiveHost = req.getServerName() + ":" + req.getServerPort();
    String ip = effectiveHost.contains(":") ? effectiveHost.split(":")[0] : effectiveHost;
    String port = effectiveHost.contains(":") ? effectiveHost.substring(effectiveHost.indexOf(":") + 1) : String.valueOf(req.getServerPort());
    log.debug("isCurrentServerBranch check: branchIP={}, branchPort={}, requestIP={}, requestPort={}",
        branch.getPIp(), branch.getPPort(), ip, port);
    return branch.getPIp().equals(ip) && branch.getPPort().equals(port);
  }

  public static class RecordSearchRequest {
    private List<String> audioFiles;
    public List<String> getAudioFiles() { return audioFiles; }
    public void setAudioFiles(List<String> audioFiles) { this.audioFiles = audioFiles; }
  }

  @LogActivity(type = "record", activity = "조회", contents = "전체 녹취 목록 조회")
  @PostMapping
  public ResponseEntity<Map<String, Object>> listAll(
      @RequestParam(name = "number",      required = false) String numberParam,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestBody(required = false) RecordSearchRequest request,
      @RequestParam(name = "page",        defaultValue = "0")  int page,
      @RequestParam(name = "size",        defaultValue = "10") int size,
      @RequestParam(name = "direction",   defaultValue = "ALL") String directionParam,
      @RequestParam(name = "numberKind",  defaultValue = "ALL") String numberKindParam,
      @RequestParam(name = "q",           required = false) String qParam,
      @RequestParam(name = "s",           required = false) String sParam,
      @RequestParam(name = "start",       required = false) String startStr,
      @RequestParam(name = "end",         required = false) String endStr
  ) {
    Info me = requireLogin(authHeader);
    Pageable reqPage = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "callStartDateTime"));
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = StringUtils.hasText(startStr) ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end   = StringUtils.hasText(endStr)   ? LocalDateTime.parse(endStr,   fmt) : null;

    Page<TrecordDto> paged;
    long inboundCount = 0, outboundCount = 0;

    // STT 키워드 경로
    if (StringUtils.hasText(sParam) && (request == null || request.getAudioFiles() == null || request.getAudioFiles().isEmpty())) {
      Map<String, Object> fnPage = sttSearchService.searchFilenames(sParam, 0, 1000);
      @SuppressWarnings("unchecked")
      List<String> esNames = (List<String>) fnPage.getOrDefault("filenames", Collections.emptyList());

      List<String> basenames = esNames.stream()
          .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
          .map(s -> s.replace('\\','/'))
          .map(s -> { int i = s.lastIndexOf('/'); return (i >= 0 ? s.substring(i+1) : s); })
          .map(String::toLowerCase)
          .map(s -> s.endsWith(".wav") ? s : (s + ".wav"))
          .distinct().toList();

      if (basenames.isEmpty()) return ResponseEntity.ok(buildPaginatedResponse(Page.empty(reqPage), 0, 0));

      String numArg = StringUtils.hasText(numberParam) ? numberParam : qParam;

      String nk = numberKindParam;
      if ("ALL".equalsIgnoreCase(nk) && StringUtils.hasText(numArg)) {
        String digits = numArg.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) nk = "EXT";
      }

      paged = recordSvc.searchByAudioBasenamesWithFilters(basenames, directionParam, nk, numArg, start, end, reqPage);

      if ("EXT".equalsIgnoreCase(nk) && StringUtils.hasText(numArg)) {
        String ext = numArg.replaceAll("[^0-9]", "");
        if (ext.length() == 3) ext = "0" + ext;
        if (ext.length() > 4)  ext = ext.substring(ext.length() - 4);
        final String fext = ext;
        List<TrecordDto> safe = paged.getContent().stream()
            .filter(r -> fext.equals(r.getNumber1())).toList();
        paged = new PageImpl<>(safe, reqPage, safe.size());
      }

      if ("ALL".equalsIgnoreCase(directionParam)) {
        inboundCount  = paged.stream().filter(r -> "수신".equals(r.getIoDiscdVal())).count();
        outboundCount = paged.getTotalElements() - inboundCount;
      } else if ("IN".equalsIgnoreCase(directionParam)) inboundCount = paged.getTotalElements();
      else outboundCount = paged.getTotalElements();

      Map<String, Integer> extSttMap = recOnDataService.parseSttStatusFromIni();
      paged.getContent().forEach(rec -> {
        String ext = rec.getNumber1() != null ? rec.getNumber1().replaceAll("^0+", "") : null;
        rec.setSttEnabled(extSttMap.getOrDefault(ext, 0));
      });
      if (!"3".equals(me.getUserLevel())) paged.getContent().forEach(rec -> rec.setJsonExists(null));
      paged.getContent().forEach(r -> {
        r.setListenUrl("/api/records/" + r.getRecordSeq() + "/listen");
        r.setDownloadUrl("/api/records/" + r.getRecordSeq() + "/download");
      });
      return ResponseEntity.ok(buildPaginatedResponse(paged, inboundCount, outboundCount));
    }

    // POST body: audioFiles
    List<String> audioFiles = (request != null ? request.getAudioFiles() : null);
    if (audioFiles != null && !audioFiles.isEmpty()) {
      Page<TrecordDto> pageAll = recordSvc.searchByAudioFileNames(
          audioFiles,
          PageRequest.of(0, audioFiles.size(), Sort.by(Sort.Direction.DESC, "callStartDateTime")));
      List<TrecordDto> allResults = pageAll.getContent();

      List<TrecordDto> filtered = allResults.stream()
          .filter(rec -> filterByDirection(rec, directionParam))
          .filter(rec -> filterByQuery(rec, qParam))
          .filter(rec -> filterByDate(rec, start, end)).toList();

      paged = new PageImpl<>(paginateList(filtered, page, size), reqPage, filtered.size());
      if ("ALL".equalsIgnoreCase(directionParam)) {
        inboundCount = filtered.stream().filter(r -> "수신".equals(r.getIoDiscdVal())).count();
        outboundCount = filtered.stream().filter(r -> "발신".equals(r.getIoDiscdVal())).count();
      } else if ("IN".equalsIgnoreCase(directionParam)) inboundCount = filtered.size();
      else outboundCount = filtered.size();

    } else {
      String lvl = me.getUserLevel();
      String searchQuery = StringUtils.hasText(numberParam) ? numberParam : qParam;

      if ("0".equals(lvl) || "3".equals(lvl)) {
        paged = recordSvc.search(null, null, directionParam, numberKindParam, searchQuery, start, end, reqPage);
        if ("ALL".equalsIgnoreCase(directionParam)) {
          inboundCount  = recordSvc.search(null, null, "IN",  numberKindParam, searchQuery, start, end, PageRequest.of(0, 1)).getTotalElements();
          outboundCount = recordSvc.search(null, null, "OUT", numberKindParam, searchQuery, start, end, PageRequest.of(0, 1)).getTotalElements();
        }
      } else {
        List<String> accessibleNumbers = ("1".equals(lvl))
            ? memberSvc.listUsersInBranch(me.getBranchSeq(), PageRequest.of(0, Integer.MAX_VALUE))
            .getContent().stream().map(Info::getNumber).filter(StringUtils::hasText)
            .map(this::normalizeToFourDigit).filter(Objects::nonNull).toList()
            : getAccessibleNumbers(me.getUserId());

        if (accessibleNumbers.isEmpty() && !StringUtils.hasText(searchQuery)) {
          paged = Page.empty(reqPage);
        } else {
          paged = recordSvc.searchByMixedNumbers(accessibleNumbers, directionParam, numberKindParam, searchQuery, start, end, reqPage);
        }

        if ("ALL".equalsIgnoreCase(directionParam)) {
          if (accessibleNumbers.isEmpty() && !StringUtils.hasText(searchQuery)) {
            inboundCount = outboundCount = 0;
          } else {
            inboundCount  = recordSvc.searchByMixedNumbers(accessibleNumbers, "IN",  numberKindParam, searchQuery, start, end, PageRequest.of(0, 1)).getTotalElements();
            outboundCount = recordSvc.searchByMixedNumbers(accessibleNumbers, "OUT", numberKindParam, searchQuery, start, end, PageRequest.of(0, 1)).getTotalElements();
          }
        } else if ("IN".equalsIgnoreCase(directionParam)) inboundCount = paged.getTotalElements();
        else outboundCount = paged.getTotalElements();
      }
    }

    Map<String, Integer> extSttMap = recOnDataService.parseSttStatusFromIni();
    paged.getContent().forEach(rec -> {
      String ext = rec.getNumber1() != null ? rec.getNumber1().replaceAll("^0+", "") : null;
      rec.setSttEnabled(extSttMap.getOrDefault(ext, 0));
    });

    paged.getContent().forEach(rec -> {
      String csdt = rec.getCallStartDateTime();
      if (csdt != null) {
        String dateDir = csdt.substring(0, 10).replace("-", "");
        String afd = rec.getAudioFileDir().replace("\\", "/").replaceFirst("^\\.\\./+", "");
        String fname = afd.substring(afd.lastIndexOf('/') + 1);
        boolean exists = recOnDataService.isJsonGenerated(dateDir, fname);
        rec.setJsonExists(exists);
      } else rec.setJsonExists(false);
    });
    if (!"3".equals(me.getUserLevel())) paged.getContent().forEach(rec -> rec.setJsonExists(null));
    paged.getContent().forEach(r -> {
      r.setListenUrl("/api/records/" + r.getRecordSeq() + "/listen");
      r.setDownloadUrl("/api/records/" + r.getRecordSeq() + "/download");
    });

    return ResponseEntity.ok(buildPaginatedResponse(paged, inboundCount, outboundCount));
  }

  private void postProcessRecordDto(TrecordDto rec, Info me) {
    if (rec.getNumber1() != null) rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
    if (me.getMaskFlag() != null && me.getMaskFlag() == 0) rec.maskNumber2();

    boolean canListen = "0".equals(me.getUserLevel()) || "1".equals(me.getUserLevel())
        || ("2".equals(me.getUserLevel()) && hasPermissionForNumber(me.getUserId(), rec.getNumber1(), 3));
    try {
      Field f = TrecordDto.class.getDeclaredField("listenAuth");
      f.setAccessible(true);
      f.set(rec, canListen ? "가능" : "불가능");
    } catch (Exception ignored) {}

    rec.setListenUrl("/api/records/" + rec.getRecordSeq() + "/listen");
    rec.setDownloadUrl("/api/records/" + rec.getRecordSeq() + "/download");

    String csdt = rec.getCallStartDateTime();
    if (csdt != null) {
      String dateDir = csdt.substring(0,10).replace("-", "");
      String afd = rec.getAudioFileDir().replace("\\","/").replaceFirst("^\\.\\./+","");
      String fname = afd.substring(afd.lastIndexOf('/') + 1);
      boolean exists = recOnDataService.isJsonGenerated(dateDir, fname);
      rec.setJsonExists(exists);
    } else rec.setJsonExists(false);
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
    if (fromIndex >= list.size()) return Collections.emptyList();
    return list.subList(fromIndex, toIndex);
  }

  @LogActivity(type = "record", activity = "조회", contents = "번호 검색")
  @GetMapping("/search")
  public ResponseEntity<Page<TrecordDto>> searchByNumbers(
      @RequestParam(value = "numbers",  required = false) String numbersCsv,
      @RequestParam(value = "number1",  required = false) String number1,
      @RequestParam(value = "number2",  required = false) String number2,
      @RequestParam(name   = "direction",   defaultValue = "ALL") String direction,
      @RequestParam(name   = "numberKind",  defaultValue = "ALL") String numberKind,
      @RequestParam(value = "audioFiles",   required = false) String audioFilesCsv,
      @RequestParam(name   = "q",           required = false) String q,
      @RequestParam(name   = "start",       required = false) String startStr,
      @RequestParam(name   = "end",         required = false) String endStr,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size
  ) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = StringUtils.hasText(startStr) ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end   = StringUtils.hasText(endStr)   ? LocalDateTime.parse(endStr,   fmt) : null;

    Info me = requireLogin(authHeader);
    Pageable pr = PageRequest.of(page, size);
    Page<TrecordDto> paged;

    if (StringUtils.hasText(audioFilesCsv)) {
      List<String> dirs = Arrays.stream(audioFilesCsv.split(","))
          .map(String::trim).filter(s -> !s.isEmpty()).toList();
      paged = recordSvc.searchByAudioFileNames(dirs, pr);
    } else {
      String lvl = me.getUserLevel();
      if ("0".equals(lvl) || "1".equals(lvl) || "3".equals(lvl)) {
        if (StringUtils.hasText(numbersCsv)) {
          List<String> nums = Arrays.stream(numbersCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
          paged = recordSvc.searchByMixedNumbers(nums, direction, numberKind, q, start, end, pr);
        } else {
          paged = recordSvc.search(number1, number2, direction, numberKind, q, start, end, pr);
        }
      } else if ("2".equals(lvl)) {
        List<String> accessible = getAccessibleNumbers(me.getUserId());
        List<String> searchNumbers;
        if (StringUtils.hasText(numbersCsv)) {
          searchNumbers = Arrays.stream(numbersCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
              .filter(accessible::contains).toList();
        } else searchNumbers = accessible;

        paged = searchNumbers.isEmpty() ? Page.empty(pr)
            : recordSvc.searchByMixedNumbers(searchNumbers, direction, numberKind, q, start, end, pr);
      } else throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다. 관리자에게 문의하세요.");
    }

    paged.getContent().forEach(rec -> postProcessRecordDto(rec, me));
    return ResponseEntity.ok(paged);
  }

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
      if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "조회 권한이 없습니다. 관리자에게 문의하세요.");
    } else if (!"0".equals(lvl) && !"1".equals(lvl) && !"3".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다. 관리자에게 문의하세요.");
    }

    if (dto.getNumber1() != null) dto.setNumber1(convertToExtensionDisplay(dto.getNumber1()));
    if (dto.getNumber2() != null) dto.setNumber2(convertToExtensionDisplay(dto.getNumber2()));
    if (me.getMaskFlag() != null && me.getMaskFlag() == 0) dto.maskNumber2();
    return ResponseEntity.ok(dto);
  }

  private StreamingResponseBody buildStream(Resource audio) throws Exception {
    InputStream raw = audio.getInputStream();
    InputStream in = audio.getFilename().toLowerCase().endsWith(".aes")
        ? CryptoUtil.decryptingStream(raw, cryptoProps.getSecretKey())
        : raw;
    return out -> { try (InputStream is = in) { StreamUtils.copy(is, out); } };
  }

  // 재생
  @LogActivity(type = "record", activity = "청취",     contents = "#{#recordSvc.getFile(#id).filename}")
  @GetMapping("/{id}/listen")
  public ResponseEntity<StreamingResponseBody> listen(
      HttpServletRequest request,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      @PathVariable("id") Integer id) throws Exception {

    cleanOldTempFiles();
    Info me = requireLogin(authHeader);

    TrecordDto recDto = recordSvc.findById(id);
    boolean canListen =
        hasPermissionForNumber(me.getUserId(), recDto.getNumber1(), 3)
            || hasPermissionForNumber(me.getUserId(), recDto.getNumber2(), 3)
            || "0".equals(me.getUserLevel()) || "1".equals(me.getUserLevel()) || "3".equals(me.getUserLevel());
    if (!canListen) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음");

    Resource audio = recordSvc.getFile(id);
    if (audio == null || !audio.exists() || !audio.isReadable())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일 없음: " + id);

    String filename = audio.getFilename();
    boolean isEncMp3 = filename != null && filename.toLowerCase().endsWith("_enc.mp3");

    // enc.mp3 → 임시 mp3로 완전 복호화 + Range 지원
    if (isEncMp3) {
      File dec = CryptoUtil.decryptToTempLegacyEncMp3(audio, filename);
      long fullLen = dec.length();

      List<HttpRange> ranges = HttpRange.parseRanges(request.getHeader(HttpHeaders.RANGE));
      if (!ranges.isEmpty()) {
        HttpRange r = ranges.get(0);
        long start = r.getRangeStart(fullLen);
        long end   = r.getRangeEnd(fullLen);
        long part  = end - start + 1;

        RandomAccessFile raf = new RandomAccessFile(dec, "r");
        raf.seek(start);
        StreamingResponseBody body = out -> {
          try (InputStream is = new FileInputStream(raf.getFD())) {
            byte[] buf = new byte[8192];
            long remain = part; int read;
            while (remain > 0 && (read = is.read(buf, 0, (int)Math.min(buf.length, remain))) != -1) {
              out.write(buf, 0, read); out.flush(); remain -= read;
            }
          } finally { raf.close(); dec.delete(); }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .contentType(MediaType.valueOf("audio/mpeg"))
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fullLen))
            .contentLength(part)
            .body(body);
      }

      StreamingResponseBody body = out -> {
        try (InputStream is = new FileInputStream(dec)) { StreamUtils.copy(is, out); }
        finally { dec.delete(); }
      };
      return ResponseEntity.ok()
          .contentType(MediaType.valueOf("audio/mpeg"))
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .contentLength(fullLen)
          .body(body);
    }

    // 나머지(.wav, .mp3, .aes)
    List<HttpRange> ranges = HttpRange.parseRanges(request.getHeader(HttpHeaders.RANGE));
    boolean isAes = filename != null && filename.toLowerCase().endsWith(".aes");
    MediaType mediaType = MediaTypeFactory.getMediaType(audio).orElse(MediaType.APPLICATION_OCTET_STREAM);

    if (!ranges.isEmpty() && !isAes) {
      long rawLength = audio.contentLength();
      HttpRange range = ranges.get(0);
      long start = range.getRangeStart(rawLength);
      long end   = range.getRangeEnd(rawLength);
      long len   = end - start + 1;

      InputStream rangeStream = openRangeStream(audio, start);
      StreamingResponseBody body = out -> {
        try (InputStream is = rangeStream) {
          byte[] buffer = new byte[8192];
          long remain = len; int read;
          while (remain > 0 && (read = is.read(buffer, 0, (int)Math.min(buffer.length, remain))) != -1) {
            out.write(buffer, 0, read); out.flush(); remain -= read;
          }
        }
      };
      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
          .contentType(mediaType)
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, rawLength))
          .contentLength(len)
          .body(body);
    }

    StreamingResponseBody fullBody;
    if (isAes) {
      InputStream dec = CryptoUtil.decryptingStream(audio.getInputStream(), cryptoProps.getSecretKey());
      fullBody = out -> { try (InputStream is = dec) { StreamUtils.copy(is, out); } };
    } else {
      fullBody = out -> { try (InputStream is = audio.getInputStream()) { StreamUtils.copy(is, out); } };
    }
    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .body(fullBody);
  }

  @LogActivity(type = "record", activity = "다운로드", contents = "#{#recordSvc.getLocalFile(#id).filename}")
  @GetMapping("/{id}/download")
  public ResponseEntity<StreamingResponseBody> downloadById(HttpServletRequest request, @PathVariable Integer id) throws Exception {
    Info me = requireLogin(request);
    TrecordDto rec = recordSvc.findById(id);
    boolean canDownload =
        hasPermissionForNumber(me.getUserId(), rec.getNumber1(), 4)
            || hasPermissionForNumber(me.getUserId(), rec.getNumber2(), 4)
            || "0".equals(me.getUserLevel()) || "1".equals(me.getUserLevel()) || "3".equals(me.getUserLevel());
    if (!canDownload) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다운로드 권한이 없습니다.");

    Resource audio = recordSvc.getLocalFile(id);
    if (audio == null || !audio.exists() || !audio.isReadable())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다: " + id);

    String raw = audio.getFilename();
    boolean isAes    = raw != null && raw.toLowerCase().endsWith(".aes");
    boolean isEncMp3 = raw != null && raw.toLowerCase().endsWith("_enc.mp3");

    String downloadName = raw;
    if (raw != null) {
      if (isAes)    downloadName = raw.substring(0, raw.length()-4);
      if (isEncMp3) downloadName = raw.replace("_enc.mp3", ".mp3");
    }

    ContentDisposition cd = ContentDisposition.attachment().filename(downloadName, StandardCharsets.UTF_8).build();
    MediaType mediaType = MediaTypeFactory.getMediaType(audio).orElse(MediaType.APPLICATION_OCTET_STREAM);

    StreamingResponseBody body;
    if (isAes) {
      InputStream dec = CryptoUtil.decryptingStream(audio.getInputStream(), cryptoProps.getSecretKey());
      body = out -> { try (InputStream is = dec) { StreamUtils.copy(is, out); } };
    } else if (isEncMp3) {
      InputStream dec = CryptoUtil.decryptingStreamLegacyEncMp3(audio.getInputStream(), raw);
      body = out -> { try (InputStream is = dec) { StreamUtils.copy(is, out); } };
    } else {
      body = buildStream(audio);
    }

    ResponseEntity.BodyBuilder b = ResponseEntity.ok().contentType(mediaType).header(HttpHeaders.CONTENT_DISPOSITION, cd.toString());
    if (!isAes && !isEncMp3) b = b.contentLength(audio.contentLength());
    return b.body(body);
  }

  private void serveLocalFile(Integer id, HttpServletResponse response) throws Exception {
    Resource file = recordSvc.getFile(id);
    if (file == null || !file.exists() || !file.isReadable()) {
      response.sendError(HttpStatus.NOT_FOUND.value(), "파일을 찾을 수 없습니다: " + id);
      return;
    }

    MediaType mediaType = MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM);
    response.setContentType(mediaType.toString());
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + UriUtils.encode(file.getFilename(), StandardCharsets.UTF_8) + "\"");
    response.setContentLengthLong(file.contentLength());
    StreamingResponseBody body = buildStream(file);
    try (ServletOutputStream out = response.getOutputStream()) { body.writeTo(out); }
  }

  private void proxyFileRequest(TbranchEntity targetBranch, Integer recordId, String action,
      HttpServletRequest request, HttpServletResponse response) {
    String targetUrl = String.format("http://%s:%s/api/records/%d/%s",
        targetBranch.getPIp(), targetBranch.getPPort(), recordId, action);
    try {
      restTemplate.execute(
          URI.create(targetUrl),
          HttpMethod.GET,
          clientRequest -> {
            Collections.list(request.getHeaderNames()).forEach(headerName -> {
              if (!headerName.equalsIgnoreCase(HttpHeaders.HOST) &&
                  !headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                clientRequest.getHeaders().add(headerName, request.getHeader(headerName));
              }
            });
          },
          clientResponse -> {
            response.setStatus(clientResponse.getStatusCode().value());
            clientResponse.getHeaders().forEach((name, values) -> {
              if (!name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) values.forEach(value -> response.addHeader(name, value));
            });
            StreamUtils.copy(clientResponse.getBody(), response.getOutputStream());
            return null;
          }
      );
    } catch (Exception e) {
      log.error("원격 지점({})으로의 프록시 요청 실패: {}", targetUrl, e.getMessage());
      try { response.sendError(HttpStatus.BAD_GATEWAY.value(), "원격 서버로부터 파일을 가져오는 데 실패했습니다."); }
      catch (IOException ioException) { log.error("프록시 실패 에러 응답 전송 중 오류 발생", ioException); }
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
    if (!("0".equals(me.getUserLevel()) || "3".equals(me.getUserLevel()))
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

  @LogActivity(
      type     = "record",
      activity = "STT 변환",
      contents = "#{ '사용자 ' + #userId + '이(가) 녹취파일 ' + #recordSvc.getLocalFile(#recordId).filename + ' 텍스트 변환' }"
  )
  @PostMapping("/{recordId}/stt-click")
  public ResponseEntity<Void> logSttClick(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @PathVariable Long recordId) {
    return ResponseEntity.ok().build();
  }

  private boolean isEncryptedName(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    return n.endsWith("_enc.mp3") || n.endsWith(".aes") || n.contains("_enc.");
  }

  private Path decryptToTemp(Path encPath) throws IOException {
    cleanOldTempFiles();

    String fileName = encPath.getFileName().toString();
    String lower = fileName.toLowerCase(Locale.ROOT);

    String key = encPath.toAbsolutePath() + "|" + Files.size(encPath) + "|" + Files.getLastModifiedTime(encPath).toMillis();
    String cacheName = "decrypted-" + Integer.toHexString(key.hashCode()) + ".tmp";
    Path out = Paths.get(System.getProperty("java.io.tmpdir")).resolve(cacheName);
    if (Files.exists(out) && Files.size(out) > 0) return out;

    Files.createDirectories(out.getParent());

    try (InputStream fin = Files.newInputStream(encPath);
        OutputStream fout = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

      if (lower.endsWith("_enc.mp3")) {
        try (InputStream dec = CryptoUtil.decryptingStreamLegacyEncMp3(fin, fileName)) {
          dec.transferTo(fout);
        }
      } else if (lower.endsWith(".aes")) {
        try {
          File tmpIn = encPath.toFile();
          File tmpOut = out.toFile();
          CryptoUtil.decryptFile(tmpIn, tmpOut, cryptoProps.getSecretKey());
        } catch (Exception e) {
          throw new IOException("AES 파일 복호화 실패", e);
        }
      } else {
        fin.transferTo(fout);
      }
    } catch (Exception e) {
      try { Files.deleteIfExists(out); } catch (Exception ignore) {}
      if (e instanceof IOException) throw (IOException)e;
      throw new IOException(e);
    }
    return out;
  }

  private Resource toPlayableResource(Resource raw) throws IOException {
    String name = raw.getFilename();
    if (name == null) return raw;
    String lower = name.toLowerCase(Locale.ROOT);
    if (!(lower.endsWith("_enc.mp3") || lower.endsWith(".aes"))) return raw;

    Path enc = raw.getFile().toPath();
    Path dec = decryptToTemp(enc);
    return new org.springframework.core.io.FileSystemResource(dec.toFile());
  }
}
