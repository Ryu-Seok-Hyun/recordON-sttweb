// src/main/java/com/sttweb/sttweb/controller/TrecordController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TrecordDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.ListResponse;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TmemberRoleService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.TrecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class TrecordController {

  private final TrecordService recordSvc;
  private final TmemberService memberSvc;
  private final TmemberRoleService roleSvc;
  private final JwtTokenProvider jwtTokenProvider;

  private void requireRole(String authHeader, int minRole) {
    String token = authHeader.substring(7);
    Integer roleSeq = memberSvc.getRoleSeqOf(
        memberSvc.getMyInfoByUserId(jwtTokenProvider.getUserId(token)).getMemberSeq());
    if (roleSeq < minRole) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
  }

  /** 전체 녹취 조회 */
  @LogActivity(
      type     = "record",
      activity = "전체조회",
      contents = "'page=' + #page + ',size=' + #size"
  )
  @GetMapping
  public ResponseEntity<Page<TrecordDto>> listAll(
      @RequestHeader("Authorization") String authHeader,
      @RequestParam(name="page", defaultValue="0") int page,
      @RequestParam(name="size", defaultValue="10") int size
  ) {
    requireRole(authHeader, 2);
    Page<TrecordDto> paged = recordSvc.findAll(PageRequest.of(page, size));
    return ResponseEntity.ok(paged);
  }

  /** 번호 검색 */
  @LogActivity(
      type     = "record",
      activity = "검색",
      contents = "'number1=' + #number1 + ',number2=' + #number2"
  )
  @GetMapping("/search")
  public ResponseEntity<Page<TrecordDto>> searchByNumber(
      @RequestHeader("Authorization") String authHeader,
      @RequestParam(value="number1", required=false) String number1,
      @RequestParam(value="number2", required=false) String number2,
      @RequestParam(name="page", defaultValue="0") int page,
      @RequestParam(name="size", defaultValue="10") int size
  ) {
    requireRole(authHeader, 2);
    Page<TrecordDto> paged = recordSvc.searchByNumber(number1, number2, PageRequest.of(page, size));
    return ResponseEntity.ok(paged);
  }

  /** 단건 조회 */
  @LogActivity(
      type     = "record",
      activity = "단건조회",
      contents = "#id.toString()"
  )
  @GetMapping("/{id}")
  public ResponseEntity<TrecordDto> getById(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
    }
    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    }
    Info me = memberSvc.getMyInfoByUserId(jwtTokenProvider.getUserId(token));
    Integer roleSeq = memberSvc.getRoleSeqOf(me.getMemberSeq());
    if ((roleSeq == 2 || roleSeq == 3)
        && !me.getNumber().equals(recordSvc.findById(id).getNumber1())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인자료 외에 검색할 수 없습니다.");
    }
    TrecordDto dto = recordSvc.findById(id);
    return ResponseEntity.ok(dto);
  }

  /** 녹취 등록 */
  @LogActivity(
      type     = "record",
      activity = "등록",
      contents = "#dto.toString()"
  )
  @PostMapping
  public ResponseEntity<TrecordDto> create(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestBody TrecordDto dto
  ) {
    String token = authHeader.substring(7);
    Info me = memberSvc.getMyInfoByUserId(jwtTokenProvider.getUserId(token));
    if (memberSvc.getRoleSeqOf(me.getMemberSeq()) < 4) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
    }
    TrecordDto created = recordSvc.create(dto);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /** 녹취 수정 */
  @LogActivity(
      type     = "record",
      activity = "수정",
      contents = "#id.toString()"
  )
  @PutMapping("/{id}")
  public ResponseEntity<TrecordDto> update(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id,
      @RequestBody TrecordDto dto
  ) {
    String token = authHeader.substring(7);
    Info me = memberSvc.getMyInfoByUserId(jwtTokenProvider.getUserId(token));
    if (memberSvc.getRoleSeqOf(me.getMemberSeq()) < 4) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
    }
    TrecordDto updated = recordSvc.update(id, dto);
    return ResponseEntity.ok(updated);
  }

  /** 녹취 삭제 */
  @LogActivity(
      type     = "record",
      activity = "삭제",
      contents = "#id.toString()"
  )
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("id") Integer id
  ) {
    String token = authHeader.substring(7);
    Info me = memberSvc.getMyInfoByUserId(jwtTokenProvider.getUserId(token));
    if (memberSvc.getRoleSeqOf(me.getMemberSeq()) < 4) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
    }
    recordSvc.delete(id);
    return ResponseEntity.noContent().build();
  }

}
