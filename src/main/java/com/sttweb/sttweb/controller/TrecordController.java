package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.PermissionService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import java.io.IOException;
import org.springframework.core.io.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private final TrecordService recordSvc;
  private final TmemberService memberSvc;
  private final TrecordService recordService;
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

  /**
   * 로그인만 하면 OK (role ≥ 1)
   *
   * @param authHeader Authorization 헤더
   * @return 현재 로그인한 사용자 정보
   */
  private Info requireLogin(String authHeader) {
    String token = extractToken(authHeader);
    return getCurrentUser(token);
  }

  /**
   * 전체 녹취 조회 (관리자는 전체, 그 외는 본인 자료만)
   */
  @LogActivity(type = "record", activity = "조회", contents = "전체 녹취 조회")
  @GetMapping
  public ResponseEntity<Page<TrecordDto>> listAll(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    Info me = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    PageRequest pr = PageRequest.of(page, size);

    Page<TrecordDto> paged = (roleSeq >= 4)
        ? recordSvc.findAll(pr)                        // 관리자: 전체 조회
        : recordSvc.findByUserNumber(me.getNumber(), pr);  // 일반 유저: 본인 자료만

    return ResponseEntity.ok(paged);
  }

  /**
   * 번호 검색 (관리자는 전체, 그 외는 본인 번호만)
   */
  @LogActivity(type = "record", activity = "조회", contents = "'number1=' + #number1 + ',number2=' + #number2")
  @GetMapping("/search")
  public ResponseEntity<Page<TrecordDto>> searchByNumber(
      @RequestParam(name = "number1") String number1,
      @RequestParam(name = "number2") String number2,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    System.out.println("[DEBUG] Authorization 헤더 → `" + authHeader + "`");
    Info me = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    PageRequest pr = PageRequest.of(page, size);

    if (roleSeq >= 4) {
      // 관리자: 자유 검색
      return ResponseEntity.ok(
          recordSvc.searchByNumber(number1, number2, pr)
      );
    } else {
      // 일반 유저: 본인 번호만 허용
      String myNum = me.getNumber();
      if (!myNum.equals(number1) && !myNum.equals(number2)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 자료 외에 검색할 수 없습니다.");
      }
      return ResponseEntity.ok(
          recordSvc.searchByNumber(number1, number2, pr)
      );
    }
  }

  /**
   * 단건 조회 (관리자는 전체, 그 외는 본인 자료만)
   */
  @LogActivity(type = "record", activity = "조회", contents = "단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<TrecordDto> getById(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    Info me = requireLogin(authHeader);
    int roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    TrecordDto dto = recordSvc.findById(id);

    if (roleSeq < 4) {
      // 일반 유저: 본인 자료만
      if (!me.getNumber().equals(dto.getNumber1()) && !me.getNumber().equals(dto.getNumber2())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 자료 외에 조회할 수 없습니다.");
      }
    }

    return ResponseEntity.ok(dto);
  }

  /**
   * 녹취 등록 (관리자 전용)
   */
  @LogActivity(type = "record", activity = "등록", contents = "녹취 등록")
  @PostMapping
  public ResponseEntity<TrecordDto> create(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestBody TrecordDto dto
  ) {
    // 관리자(4)만
    Info me = requireLogin(authHeader);
    if (memberSvc.getRoleSeqOf(me.getMemberSeq()) < 4) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
    }
    TrecordDto created = recordSvc.create(dto);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * 녹취 수정 (관리자 전용)
   */
  @LogActivity(type = "record", activity = "수정", contents = "녹취 수정")
  @PutMapping("/{id}")
  public ResponseEntity<TrecordDto> update(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable Integer id,
      @RequestBody TrecordDto dto
  ) {
    Info me = requireLogin(authHeader);
    if (memberSvc.getRoleSeqOf(me.getMemberSeq()) < 4) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
    }
    TrecordDto updated = recordSvc.update(id, dto);
    return ResponseEntity.ok(updated);
  }

  /**
   * 녹취 삭제 (관리자 전용)
   */
  @LogActivity(type = "record", activity = "삭제 ", contents = "녹취 삭제")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable Integer id
  ) {
    Info me = requireLogin(authHeader);
    if (memberSvc.getRoleSeqOf(me.getMemberSeq()) < 4) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
    }
    recordSvc.delete(id);
    return ResponseEntity.noContent().build();
  }

  // 1) 단순 조회(READ) 권한이 필요할 때
  @LogActivity(type = "record", activity = "조회", contents = "사용자별 녹취 조회")
  @GetMapping("/user/{targetUserSeq}")
  @PreAuthorize(
      "hasRole('ADMIN') or principal.memberSeq == #targetUserSeq or @permService.hasLevel(principal.memberSeq, #targetUserSeq, 1)"
  )
  public ResponseEntity<Page<TrecordDto>> listRecords(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable Integer targetUserSeq,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    requireLogin(authHeader);
    Info target = memberSvc.getMyInfoByMemberSeq(targetUserSeq);
    PageRequest pr = PageRequest.of(page, size);
    return ResponseEntity.ok(recordSvc.findByUserNumber(target.getNumber(), pr));
  }

  @LogActivity(type = "record", activity = "청취", contents = "녹취 청취")
  @GetMapping("/user/{targetUserSeq}/listen")
  @PreAuthorize(
      "hasRole('ADMIN') or principal.memberSeq == #targetUserSeq or @permService.hasLevel(principal.memberSeq, #targetUserSeq, 2)"
  )
  public ResponseEntity<byte[]> listenRecords(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable Integer targetUserSeq,
      @RequestParam(name = "recordId") Integer recordId
  ) {
    byte[] audio = recordService.getAudioByIdAndUserSeq(recordId, targetUserSeq);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
        .body(audio);
  }

  @LogActivity(type = "record", activity = "다운로드", contents = "녹취 다운로드")
  @GetMapping("/user/{targetUserSeq}/download")
  @PreAuthorize(
      "hasRole('ADMIN') or principal.memberSeq == #targetUserSeq or @permService.hasLevel(principal.memberSeq, #targetUserSeq, 3)"
  )
  public ResponseEntity<Resource> downloadRecords(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable Integer targetUserSeq,
      @RequestParam(name = "recordId") Integer recordId
  ) {
    Resource file = recordService.getFileByIdAndUserSeq(recordId, targetUserSeq);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + file.getFilename() + "\"")
        .body(file);
  }

//  // 다운로드
//  @GetMapping(value = "<api_url>")
//  public ResponseEntity<Resource> fileDownloadApi() throws IOException{
//    return trecordService.fileEownload();
//  }

}