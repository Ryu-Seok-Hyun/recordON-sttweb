package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.exception.ForbiddenException;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.service.TactivitylogService;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activitylogs")
@RequiredArgsConstructor
public class ActivityLogController {

  private final TactivitylogService logService;
  private final TmemberService     memberService;
  private final JwtTokenProvider   jwt;

  private String extractToken(String header){
    return (header!=null && header.startsWith("Bearer "))
        ? header.substring(7).trim() : null;
  }

  /* ─────────── 목록 조회 ─────────── */
  @GetMapping
  public ResponseEntity<Page<TactivitylogDto>> list(
      @RequestParam(defaultValue = "0")  int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required=false) String startCrtime,
      @RequestParam(required=false) String endCrtime,
      @RequestParam(required=false) String type,
      @RequestParam(required=false) String searchField,
      @RequestParam(required=false) String keyword,
      @RequestHeader(value="Authorization", required=false) String authHeader
  ){
    String token = extractToken(authHeader);
    if(token==null || !jwt.validateToken(token))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

    String userId    = jwt.getUserId(token);
    String userLevel = jwt.getUserLevel(token);

    /* 지점관리자면 자기 branchSeq 추출 */
    Integer branchSeq = null;
    if("1".equals(userLevel)){
      branchSeq = memberService.getMyInfoByUserId(userId).getBranchSeq();
    }

    Pageable pageable = PageRequest.of(page,size,Sort.by("crtime").descending());

    Page<TactivitylogDto> result =
        logService.getLogsWithFilter(
            userId, userLevel, branchSeq,
            startCrtime, endCrtime,
            type, searchField, keyword,
            pageable
        );
    return ResponseEntity.ok(result);
  }

  /* ─────────── 단건 조회 ─────────── */
  @GetMapping("/{id}")
  public ResponseEntity<TactivitylogDto> get(
      @PathVariable Integer id,
      @RequestHeader(value="Authorization",required=false) String authHeader
  ){
    String token = extractToken(authHeader);
    if(token==null || !jwt.validateToken(token))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

    String userId    = jwt.getUserId(token);
    String userLevel = jwt.getUserLevel(token);

    TactivitylogDto dto = logService.getLog(id);

    // HQ OK / 지점장은 지점 확인 / 일반은 본인만
    if("0".equals(userLevel)){
      return ResponseEntity.ok(dto);
    }
    if("1".equals(userLevel)){
      Integer myBranch = memberService.getMyInfoByUserId(userId).getBranchSeq();
      if(myBranch!=null && myBranch.equals(dto.getBranchSeq()))
        return ResponseEntity.ok(dto);
    }
    if("2".equals(userLevel) && userId.equals(dto.getUserId()))
      return ResponseEntity.ok(dto);

    throw new ForbiddenException("조회 권한이 없습니다.");
  }

  /* ─────────── 삭제 ─────────── (HQ 전용) */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable Integer id,
      @RequestHeader(value="Authorization",required=false) String authHeader
  ){
    String token = extractToken(authHeader);
    if(token==null || !jwt.validateToken(token))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

    if(!"0".equals(jwt.getUserLevel(token)))
      throw new ForbiddenException("본사 관리자만 삭제 가능합니다.");

    logService.deleteLog(id);
    return ResponseEntity.noContent().build();
  }
}
