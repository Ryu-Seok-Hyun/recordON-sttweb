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
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.sttweb.sttweb.entity.TrecordTelListEntity;
/**
 * 녹취 관리 컨트롤러
 * 녹취 데이터의 조회, 검색, 청취, 다운로드 기능을 제공
 */
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private final TrecordService           recordSvc;
  private final TrecordScanService       scanSvc;
  private final TmemberService           memberSvc;
  private final JwtTokenProvider         jwtTokenProvider;
  private final UserPermissionRepository userPermRepo;
  private final TrecordTelListRepository trecordTelListRepository;

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

  private Map<Integer, String> getLineIdToNumberMap() {
    // 모든 tmember(memberSeq, number) 조회 → Map<memberSeq, 4자리번호>
    Map<Integer, String> map = new HashMap<>();
    for (Info m : memberSvc.getAllMembers()) {
      if (m.getMemberSeq() != null && m.getNumber() != null) {
        map.put(m.getMemberSeq(), normalizeToFourDigit(m.getNumber()));
      }
    }
    return map;
  }

  private List<String> getAccessibleNumbers(String userId) {
    Set<String> numbers = new LinkedHashSet<>();
    Info me = memberSvc.getMyInfoByUserId(userId);

    // 1) 본인 내선번호
    if (me.getNumber() != null && !me.getNumber().trim().isEmpty()) {
      String n = normalizeToFourDigit(me.getNumber());
      if (n != null) numbers.add(n);
    }

    // 2) 권한받은 내선 (tuser_permission.line_id → trecord_tel_list.call_num)
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



  private boolean hasPermissionForNumber(String userId, String number, int reqLevel) {
    Info me = memberSvc.getMyInfoByUserId(userId);
    List<String> target = makeExtensions(number);

    // 본인 번호
    if (me.getNumber() != null) {
      for (String ext : makeExtensions(me.getNumber())) {
        if (target.contains(ext)) return true;
      }
    }

    // 권한 받은 번호들 (line_id → trecord_tel_list.call_num)
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





  private List<String> makeExtensions(String num) {
    if (num == null) return Collections.emptyList();
    String d = num.replaceAll("[^0-9]", "");
    Set<String> s = new LinkedHashSet<>();
    if (!d.isEmpty()) {
      s.add(d);
      if (d.length() == 3)             s.add("0" + d);
      if (d.length() > 4)              s.add(d.substring(d.length() - 4));
    }
    return new ArrayList<>(s);
  }

  private String normalizeToFourDigit(String n) {
    if (n == null) return null;
    String d = n.replaceAll("[^0-9]", "");
    if (d.length() == 4) return d;
    if (d.length() == 3) return "0" + d;
    if (d.length() > 4)  return d.substring(d.length() - 4);
    return null;
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

  @LogActivity(type="record", activity="조회", contents="전체 녹취 조회")
  @GetMapping
  public ResponseEntity<Map<String,Object>> listAll(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestParam(name="page",       defaultValue="0")   int page,
      @RequestParam(name="size",       defaultValue="10")  int size,
      @RequestParam(name="direction",  defaultValue="ALL") String direction,
      @RequestParam(name="numberKind", defaultValue="ALL") String numberKind,
      @RequestParam(name="q",          required=false)    String q,
      @RequestParam(name="start",      required=false)    String startStr,
      @RequestParam(name="end",        required=false)    String endStr
  ) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime start = startStr!=null ? LocalDateTime.parse(startStr, fmt) : null;
    LocalDateTime end   = endStr  !=null ? LocalDateTime.parse(endStr,   fmt) : null;

    Info me      = requireLogin(authHeader);
    String lvl   = me.getUserLevel();
    Pageable reqPage = PageRequest.of(page, size);

    Page<TrecordDto> paged;
    if ("0".equals(lvl)) {
      if (q!=null && q.contains(",")) {
        List<String> nums = Arrays.stream(q.split(",")).map(String::trim).toList();
        paged = recordSvc.searchByMixedNumbers(nums, reqPage);
      } else {
        paged = recordSvc.search(null, null, direction, numberKind, q, start, end, reqPage);
      }

    } else if ("1".equals(lvl)) {
      List<String> nums = memberSvc
          .listUsersInBranch(me.getBranchSeq(), PageRequest.of(0, Integer.MAX_VALUE))
          .getContent().stream()
          .map(Info::getNumber)
          .filter(Objects::nonNull)
          .map(this::normalizeToFourDigit)
          .filter(Objects::nonNull)
          .toList();
      paged = recordSvc.searchByMixedNumbers(nums, reqPage);

    } else if ("2".equals(lvl)) {
      List<String> accessible = getAccessibleNumbers(me.getUserId());
      if (accessible.isEmpty()) {
        paged = Page.empty(reqPage);
      } else {
        // searchByMixedNumbers에서 그대로 IN 쿼리!
        paged = recordSvc.searchByMixedNumbers(accessible, reqPage);
      }


    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    paged.getContent().forEach(rec -> {
//      if (rec.getNumber1()!=null) rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
//      if (rec.getNumber2()!=null) rec.setNumber2(convertToExtensionDisplay(rec.getNumber2()));
      if (rec.getNumber1()==null || rec.getNumber1().length()!=4) {
        String tmp = rec.getNumber1();
        rec.setNumber1(rec.getNumber2());
        rec.setNumber2(tmp);
      }
      boolean canL = "0".equals(lvl) || "1".equals(lvl)
          || ("2".equals(lvl)&&hasPermissionForNumber(me.getUserId(),rec.getNumber1(),3));
      try {
        Field f = TrecordDto.class.getDeclaredField("listenAuth");
        f.setAccessible(true);
        f.set(rec, canL ? "가능" : "불가능");
      } catch(Exception ignored){}
    });

    if (me.getMaskFlag()!=null && me.getMaskFlag()==0)
      paged.getContent().forEach(TrecordDto::maskNumber2);

    long inCount  = recordSvc.countByBranchAndDirection(null,"IN");
    long outCount = recordSvc.countByBranchAndDirection(null,"OUT");
    if ("1".equals(lvl)) {
      inCount  = recordSvc.countByBranchAndDirection(me.getBranchSeq(),"IN");
      outCount = recordSvc.countByBranchAndDirection(me.getBranchSeq(),"OUT");
    } else if ("2".equals(lvl)) {
      inCount = outCount = paged.getTotalElements();
    }

    Map<String,Object> body = new LinkedHashMap<>();
    body.put("content",         paged.getContent());
    body.put("totalElements",   paged.getTotalElements());
    body.put("totalPages",      paged.getTotalPages());
    body.put("size",            paged.getSize());
    body.put("number",          paged.getNumber());
    body.put("numberOfElements",paged.getNumberOfElements());
    body.put("empty",           paged.isEmpty());
    body.put("inboundCount",    inCount);
    body.put("outboundCount",   outCount);
    body.put("first",           paged.isFirst());
    body.put("last",            paged.isLast());
    body.put("pageable",        paged.getPageable());
    body.put("sort",            paged.getSort());

    return ResponseEntity.ok(body);
  }

  /**
   * 번호로 녹취 검색
   * 여러 번호를 쉼표로 구분하여 검색하거나 개별 번호로 검색 가능
   */
  @LogActivity(type="record", activity="조회", contents="번호 검색")
  @GetMapping("/search")
  public ResponseEntity<Page<TrecordDto>> searchByNumbers(
      @RequestParam(value="numbers", required=false) String numbersCsv,
      @RequestParam(value="number1",  required=false) String number1,
      @RequestParam(value="number2",  required=false) String number2,
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestParam(value="page", defaultValue="0") int page,
      @RequestParam(value="size", defaultValue="10") int size
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    Pageable pr = PageRequest.of(page, size);
    Page<TrecordDto> paged;

    // 권한에 따른 검색 처리
    if ("0".equals(lvl) || "1".equals(lvl)) {
      // 관리자 또는 지점 관리자
      if (numbersCsv != null) {
        // 여러 번호 검색
        List<String> nums = Arrays.stream(numbersCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        paged = recordSvc.searchByMixedNumbers(nums, pr);
      } else if (number1 != null || number2 != null) {
        // 개별 번호 검색
        paged = recordSvc.searchByNumber(number1, number2, pr);
      } else {
        // 전체 조회
        paged = recordSvc.findAll(pr);
      }
    } else if ("2".equals(lvl)) {
      // 일반 사용자: 접근 가능한 번호만
      List<String> accessible = getAccessibleNumbers(me.getUserId());
      if (accessible.isEmpty()) {
        paged = Page.empty(pr);
      } else {
        paged = recordSvc.searchByMixedNumbers(accessible, pr);
      }
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    // 번호 형태 변환
    paged.getContent().forEach(rec -> {
      if (rec.getNumber1() != null) rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
      if (rec.getNumber2() != null) rec.setNumber2(convertToExtensionDisplay(rec.getNumber2()));
    });

    // 마스킹 처리
    if (me.getMaskFlag() != null && me.getMaskFlag() == 0) {
      paged.getContent().forEach(TrecordDto::maskNumber2);
    }

    return ResponseEntity.ok(paged);
  }

  /**
   * ID로 녹취 단건 조회
   * 권한 체크 후 상세 정보 반환
   */
  @LogActivity(type="record", activity="조회", contents="단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<TrecordDto> getById(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    TrecordDto dto = recordSvc.findById(id);

    // 권한 체크
    if ("2".equals(lvl)) {
      // 일반 사용자: 본인 또는 권한이 있는 번호인지 확인
      boolean ok = hasPermissionForNumber(me.getUserId(), dto.getNumber1(), 2)
          || hasPermissionForNumber(me.getUserId(), dto.getNumber2(), 2);
      if (!ok) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "조회 권한이 없습니다.");
      }
    } else if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    // 번호 형태 변환
    if (dto.getNumber1() != null) dto.setNumber1(convertToExtensionDisplay(dto.getNumber1()));
    if (dto.getNumber2() != null) dto.setNumber2(convertToExtensionDisplay(dto.getNumber2()));

    // 마스킹 처리
    if (me.getMaskFlag() != null && me.getMaskFlag() == 0) {
      dto.maskNumber2();
    }

    return ResponseEntity.ok(dto);
  }

  /**
   * 녹취 파일 스트리밍 청취
   * Range 요청을 지원하여 부분 다운로드 가능
   */
  @LogActivity(type="record", activity="청취", contents="녹취 청취")
  @GetMapping(value="/{id}/listen", produces= MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<ResourceRegion> streamAudio(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestHeader HttpHeaders headers,
      HttpServletRequest request,
      @PathVariable("id") Integer id
  ) throws IOException {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    TrecordDto dto = recordSvc.findById(id);

    // 청취 권한 체크 (레벨 3 이상 필요)
    if ("2".equals(lvl)) {
      boolean ok = hasPermissionForNumber(me.getUserId(), dto.getNumber1(), 3)
          || hasPermissionForNumber(me.getUserId(), dto.getNumber2(), 3);
      if (!ok) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "청취 권한이 없습니다.");
      }
    } else if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    // 파일 리소스 조회
    Resource audio = recordSvc.getFile(id);
    long length = audio.contentLength();

    // Range 요청 처리
    List<HttpRange> ranges = headers.getRange();
    ResourceRegion region = ranges.isEmpty()
        ? new ResourceRegion(audio, 0, Math.min(1024*1024, length)) // 기본 1MB
        : ranges.get(0).toResourceRegion(audio);

    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(MediaTypeFactory.getMediaType(audio).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .eTag("\"" + id + "-" + region.getPosition() + "\"")
        .body(region);
  }

  /**
   * 녹취 파일 다운로드
   * 전체 파일을 첨부파일로 다운로드
   */
  @LogActivity(type="record", activity="다운로드", contents="녹취 다운로드")
  @GetMapping("/{id}/download")
  public ResponseEntity<Resource> downloadById(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      HttpServletRequest request,
      @PathVariable("id") Integer id
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    TrecordDto dto = recordSvc.findById(id);

    // 다운로드 권한 체크 (레벨 4 이상 필요)
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

  /**
   * 지점별 녹취 목록 조회
   * 지점 관리자 이상만 접근 가능
   */
  @GetMapping("/branch/{branchSeq}")
  public ResponseEntity<Page<TrecordDto>> listByBranch(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("branchSeq") Integer branchSeq,
      @RequestParam(name="page", defaultValue="0") int page,
      @RequestParam(name="size", defaultValue="10") int size
  ) {
    Info me = requireLogin(authHeader);

    // 권한 체크: 관리자 또는 해당 지점의 관리자만
    if (!"0".equals(me.getUserLevel())
        && !("1".equals(me.getUserLevel()) && me.getBranchSeq().equals(branchSeq))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }

    Pageable pr = PageRequest.of(page, size);
    Page<TrecordDto> result = recordSvc.findAllByBranch(branchSeq, pr);

    // 번호 형태 변환
    result.getContent().forEach(rec -> {
      if (rec.getNumber1() != null) rec.setNumber1(convertToExtensionDisplay(rec.getNumber1()));
      if (rec.getNumber2() != null) rec.setNumber2(convertToExtensionDisplay(rec.getNumber2()));
    });

    return ResponseEntity.ok(result);
  }
}