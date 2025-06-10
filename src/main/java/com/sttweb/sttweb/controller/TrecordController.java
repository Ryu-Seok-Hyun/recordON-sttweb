package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.MemberLinePermDto;
import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.MemberLinePermService;
import com.sttweb.sttweb.service.PermissionService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordScanService;
import com.sttweb.sttweb.service.TrecordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private final TrecordService           recordSvc;
  private final TrecordScanService       scanSvc;
  private final TmemberService           memberSvc;
  private final JwtTokenProvider         jwtTokenProvider;
  private final PermissionService        permService;
  private final MemberLinePermService    linePermService;

  // 토큰에서 페이로드(userId) 검증·추출
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

  // 토큰 → 사용자 정보
  private Info getCurrentUser(String token) {
    String userId = jwtTokenProvider.getUserId(token);
    return memberSvc.getMyInfoByUserId(userId);
  }

  // 로그인 필수
  private Info requireLogin(String authHeader) {
    String token = extractToken(authHeader);
    return getCurrentUser(token);
  }

  /**
   * 전화번호를 내선번호로 변환 (응답용)
   * 4자리면 그대로, 아니면 마지막 4자리 또는 그대로 유지
   */
  private String convertToExtensionDisplay(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
      return phoneNumber;
    }

    // 이미 4자리면 그대로 (내선번호로 간주)
    if (phoneNumber.length() == 4) {
      return phoneNumber;
    }

    // 하이픈으로 분리된 경우 마지막 부분을 내선번호로 표시
    if (phoneNumber.contains("-")) {
      String[] parts = phoneNumber.split("-");
      String lastPart = parts[parts.length - 1];
      // 마지막 부분이 4자리면 내선번호로 표시
      if (lastPart.length() == 4) {
        return lastPart;
      }
    }

    // 8자리 이상이면 마지막 4자리를 내선번호로 표시
    if (phoneNumber.length() >= 8) {
      return phoneNumber.substring(phoneNumber.length() - 4);
    }

    // 그 외에는 원본 그대로
    return phoneNumber;
  }

  // ---------------------------------------
  // 0) 신규 녹취 스캔 → DB 저장
  // ---------------------------------------
  @LogActivity(type = "record", activity = "등록", contents = "디스크 스캔하여 DB 저장")
  @GetMapping("/scan")
  public ResponseEntity<Map<String,Object>> scanAndSaveNewRecords(
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) throws IOException {
    requireLogin(authHeader);
    int inserted = scanSvc.scanAndSaveNewRecords();
    return ResponseEntity.ok(Map.of(
        "inserted", inserted,
        "message", "신규 녹취 " + inserted + "건이 DB에 저장되었습니다."
    ));
  }

  /**
   * 3자리 번호를 4자리 내선번호로 변환 (3자리는 제외)
   */
  private String normalizeToFourDigit(String number) {
    if (number == null || number.trim().isEmpty()) {
      return null;
    }

    // 이미 4자리면 그대로
    if (number.length() == 4) {
      return number;
    }

    // 3자리면 앞에 0 추가해서 4자리로 변환
    if (number.length() == 3) {
      return "0" + number; // 271 → 0271
    }

    // 그 외는 null (제외)
    return null;
  }

  // ---------------------------------------
  // 1) 전체 녹취 + 필터링 + IN/OUT 카운트 (기존 로직 유지 + 응답시 내선번호 변환)
  // ---------------------------------------
  @LogActivity(type = "record", activity = "조회", contents = "전체 녹취 조회")
  @GetMapping
  public ResponseEntity<Map<String,Object>> listAll(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "page",       defaultValue = "0")   int page,
      @RequestParam(name = "size",       defaultValue = "10")  int size,
      @RequestParam(name = "direction",  defaultValue = "ALL") String direction,
      @RequestParam(name = "numberKind", defaultValue = "ALL") String numberKind,
      @RequestParam(name = "q",          required = false)    String q,
      @RequestParam(name = "start",      required = false)    String startStr,
      @RequestParam(name = "end",        required = false)    String endStr
  ) {
    // ① 날짜 파싱
    DateTimeFormatter fmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    LocalDateTime    start = (startStr!=null ? LocalDateTime.parse(startStr, fmt) : null);
    LocalDateTime    end   = (  endStr!=null ? LocalDateTime.parse(endStr,   fmt) : null);

    // ② 로그인 및 사용자 정보
    Info     me    = requireLogin(authHeader);
    String   lvl   = me.getUserLevel();
    Integer  maskF = me.getMaskFlag();
    String   myNum = me.getNumber();
    Pageable pr    = PageRequest.of(page, size);

    // 3) 페이징 조회: 본사(0)/지사관리자(1)/일반(2) - 기존 로직 유지
    Page<TrecordDto> paged;
    if ("1".equals(lvl)) {
      // 지사 관리자: 본지사 전체 내선 (기존 로직 유지)
      List<String> nums = memberSvc
          .listUsersInBranch(me.getBranchSeq(), PageRequest.of(0, Integer.MAX_VALUE))
          .getContent().stream()
          .map(Info::getNumber)
          .filter(Objects::nonNull)
          .toList();

      paged = recordSvc.searchByNumbers(nums, pr);

    } else if ("2".equals(lvl)) {
      // 일반 유저: 본인 + 사용자간 권한 + 라인 권한
      Set<String> nums = new LinkedHashSet<>();
      nums.add(myNum);

      // 디버깅: 본인 번호 로그
      System.out.println("=== 권한 디버깅 ===");
      System.out.println("본인 번호: " + myNum);
      System.out.println("멤버 시퀀스: " + me.getMemberSeq());

      // 사용자 간 권한 조회
      List<String> grantedNumbers = permService.findGrantedNumbers(me.getMemberSeq());
      System.out.println("권한받은 번호들: " + grantedNumbers);

      // 권한받은 번호들을 4자리 내선번호로 변환
      for (String grantedNum : grantedNumbers) {
        String normalizedNum = normalizeToFourDigit(grantedNum);
        if (normalizedNum != null) {
          nums.add(normalizedNum);
        }
      }

      // 라인(내선) 권한 조회
      List<MemberLinePermDto> linePermissions = linePermService.getPermissionsByMember(me.getMemberSeq());
      System.out.println("라인 권한들: " + linePermissions);

      List<String> lineNumbers = linePermissions.stream()
          .map(MemberLinePermDto::getCallNum)
          .filter(Objects::nonNull)
          .toList();
      System.out.println("라인 권한 번호들: " + lineNumbers);

      // 라인 권한 번호들도 4자리 내선번호로 변환
      for (String lineNum : lineNumbers) {
        String normalizedNum = normalizeToFourDigit(lineNum);
        if (normalizedNum != null) {
          nums.add(normalizedNum);
        }
      }

      // 최종 검색할 번호 목록
      List<String> finalNumbers = new ArrayList<>(nums);
      System.out.println("최종 검색 번호들: " + finalNumbers);
      System.out.println("==================");

      paged = recordSvc.searchByNumbers(finalNumbers, pr);
    } else {
      // 본사(0): 전체 조회 or q 콤마 복합 조회 (기존 로직 유지)
      boolean multi = (q!=null && q.contains(","));
      if (multi) {
        List<String> numbers = Arrays.stream(q.split(","))
            .map(String::trim).filter(s->!s.isEmpty()).toList();
        paged = recordSvc.searchByNumbers(numbers, pr);
      } else {
        paged = recordSvc.search(
            null, null,
            direction, numberKind, q, start, end,
            pr
        );
      }
    }

    // ④ 응답시 전화번호를 내선번호로 변환해서 표시
    paged.getContent().forEach(record -> {
      if (record.getNumber1() != null) {
        String originalNumber1 = record.getNumber1();
        record.setNumber1(convertToExtensionDisplay(originalNumber1));
      }
      if (record.getNumber2() != null) {
        String originalNumber2 = record.getNumber2();
        record.setNumber2(convertToExtensionDisplay(originalNumber2));
      }
    });

    // ⑤ 마스킹 처리
    if (maskF!=null && maskF==0) {
      paged.getContent().forEach(TrecordDto::maskNumber2);
    }

    // ⑥ IN/OUT 카운트
    long inCount, outCount;
    if ("1".equals(lvl)) {
      inCount  = recordSvc.countByBranchAndDirection(me.getBranchSeq(), "IN");
      outCount = recordSvc.countByBranchAndDirection(me.getBranchSeq(), "OUT");
    }
    else if ("2".equals(lvl) || (q!=null && q.contains(","))) {
      inCount = outCount = paged.getTotalElements();
    }
    else {
      inCount  = recordSvc.countByBranchAndDirection(null, "IN");
      outCount = recordSvc.countByBranchAndDirection(null, "OUT");
    }

    // ⑦ 응답 조립
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


  // ---------------------------------------
  // 2) 번호 검색
  // ---------------------------------------
  @LogActivity(type = "record", activity = "조회", contents = "번호 검색")
  @GetMapping("/search")
  public ResponseEntity<Page<TrecordDto>> searchByNumbers(
      @RequestParam(value="numbers", required=false) String numbersCsv,
      @RequestParam(value="number1",  required=false) String number1,
      @RequestParam(value="number2",  required=false) String number2,
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestParam(value="page", defaultValue="0") int page,
      @RequestParam(value="size", defaultValue="10") int size
  ) {
    Info me     = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    Integer maskF = me.getMaskFlag();
    Pageable pr = PageRequest.of(page, size);
    Page<TrecordDto> paged;

    if (numbersCsv != null) {
      List<String> nums = Arrays.stream(numbersCsv.split(","))
          .map(String::trim).filter(s -> !s.isEmpty())
          .toList();
      if (roleSeq < 2 && nums.stream().anyMatch(n -> !n.equals(me.getNumber()))) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 자료만 검색 가능합니다.");
      }
      paged = recordSvc.searchByNumbers(nums, pr);

    } else {
      if (number1 == null && number2 == null) {
        paged = recordSvc.findAll(pr);
      } else {
        if (roleSeq < 2) {
          String my = me.getNumber();
          if ((number1 != null && !number1.equals(my)) ||
              (number2 != null && !number2.equals(my))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 자료 외에 검색할 수 없습니다.");
          }
        }
        paged = recordSvc.searchByNumber(number1, number2, pr);
      }
    }

    // 응답시 전화번호를 내선번호로 변환
    paged.getContent().forEach(record -> {
      if (record.getNumber1() != null) {
        record.setNumber1(convertToExtensionDisplay(record.getNumber1()));
      }
      if (record.getNumber2() != null) {
        record.setNumber2(convertToExtensionDisplay(record.getNumber2()));
      }
    });

    if (maskF != null && maskF == 0) {
      paged.getContent().forEach(TrecordDto::maskNumber2);
    }
    return ResponseEntity.ok(paged);
  }

  // ---------------------------------------
  // 3) 단건 조회
  // ---------------------------------------
  @LogActivity(type = "record", activity = "조회", contents = "단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<TrecordDto> getById(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    Info me     = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    Integer maskF = me.getMaskFlag();
    TrecordDto dto = recordSvc.findById(id);

    if (roleSeq < 2
        && !me.getNumber().equals(dto.getNumber1())
        && !me.getNumber().equals(dto.getNumber2())
    ) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 자료 외에 조회할 수 없습니다.");
    }

    // 응답시 전화번호를 내선번호로 변환
    if (dto.getNumber1() != null) {
      dto.setNumber1(convertToExtensionDisplay(dto.getNumber1()));
    }
    if (dto.getNumber2() != null) {
      dto.setNumber2(convertToExtensionDisplay(dto.getNumber2()));
    }

    if (maskF != null && maskF == 0) {
      dto.maskNumber2();
    }
    return ResponseEntity.ok(dto);
  }

  // ---------------------------------------
  // 4) 청취 (Partial Content)
  // ---------------------------------------
  @LogActivity(type = "record", activity = "청취", contents = "녹취 청취")
  @GetMapping(value="/{id}/listen", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<ResourceRegion> streamAudio(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestHeader HttpHeaders headers,
      HttpServletRequest request,
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
    ResourceRegion region = ranges.isEmpty()
        ? new ResourceRegion(audio, 0, Math.min(1024 * 1024, contentLength))
        : ranges.get(0).toResourceRegion(audio);

    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(MediaTypeFactory.getMediaType(audio).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .eTag("\"" + id + "-" + region.getPosition() + "\"")
        .body(region);
  }

  // ---------------------------------------
  // 5) 다운로드
  // ---------------------------------------
  @LogActivity(type = "record", activity = "다운로드", contents = "녹취 다운로드")
  @GetMapping("/{id}/download")
  public ResponseEntity<Resource> downloadById(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      HttpServletRequest request,
      @PathVariable("id") Integer id
  ) {
    Info me = requireLogin(authHeader);
    TrecordDto dto = recordSvc.findById(id);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());

    boolean hasDownloadRole = roleSeq >= 4;
    boolean isOwner        = me.getNumber().equals(dto.getNumber1()) ||
        me.getNumber().equals(dto.getNumber2());
    boolean isBranchAdmin  = "1".equals(me.getUserLevel()) &&
        me.getBranchSeq() != null &&
        me.getBranchSeq().equals(dto.getBranchSeq());
    boolean hasGrant       = dto.getOwnerMemberSeq() != null &&
        permService.hasLevel(me.getMemberSeq(),
            dto.getOwnerMemberSeq(),
            4);

    if (!(hasDownloadRole || isOwner || isBranchAdmin || hasGrant)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다운로드 권한이 없습니다.");
    }

    Resource file = recordSvc.getFile(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + file.getFilename() + "\"")
        .body(file);
  }

  // ---------------------------------------
  // 6) 특정 지점 녹취 조회
  // ---------------------------------------
  @GetMapping("/branch/{branchSeq}")
  public ResponseEntity<Page<TrecordDto>> listByBranch(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("branchSeq") Integer branchSeq,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    Info me = getCurrentUser(extractToken(authHeader));
    if (!"0".equals(me.getUserLevel())
        && !("1".equals(me.getUserLevel()) &&
        me.getBranchSeq() != null &&
        me.getBranchSeq().equals(branchSeq))
    ) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
    Pageable pr = PageRequest.of(page, size);
    Page<TrecordDto> result = recordSvc.findAllByBranch(branchSeq, pr);

    // 응답시 전화번호를 내선번호로 변환
    result.getContent().forEach(record -> {
      if (record.getNumber1() != null) {
        record.setNumber1(convertToExtensionDisplay(record.getNumber1()));
      }
      if (record.getNumber2() != null) {
        record.setNumber2(convertToExtensionDisplay(record.getNumber2()));
      }
    });

    return ResponseEntity.ok(result);
  }
}