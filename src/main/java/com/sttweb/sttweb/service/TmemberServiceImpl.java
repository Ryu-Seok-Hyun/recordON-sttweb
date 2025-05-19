package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.service.TbranchService;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TmemberServiceImpl implements TmemberService {

  private final TmemberRepository repo;
  private final PasswordEncoder passwordEncoder;
  private final HttpSession session;
  private final TbranchService branchSvc;
  private final TmemberRepository memberRepo;
  private final PermissionService permissionService;
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Transactional
  public void signup(SignupRequest req, Integer regMemberSeq, String regUserId) {
    repo.findByUserId(req.getUserId())
        .ifPresent(u -> {
          throw new IllegalArgumentException("이미 존재하는 사용자 ID입니다.");
        });

    TmemberEntity e = new TmemberEntity();
    e.setUserId(req.getUserId());
    e.setUserPass(passwordEncoder.encode(req.getUserPass()));
    e.setBranchSeq(req.getBranchSeq());
    e.setEmployeeId(req.getEmployeeId());
    e.setNumber(req.getNumber());
    e.setUserLevel("0".equals(req.getUserLevel()) ? "0" : "1");
    e.setRoleSeq(
        req.getRoleSeq() != null && req.getRoleSeq() >= 1 && req.getRoleSeq() <= 3
            ? req.getRoleSeq()
            : 1
    );
    String now = LocalDateTime.now().format(FMT);
    e.setCrtime(now);
    e.setUdtime(now);
    e.setReguserId(regUserId);

    repo.save(e);
  }
  /**
   * 회원가입 + 권한 부여 (인터페이스에 선언된 메서드)
   */
  @Override
  @Transactional
  public void signupWithGrants(SignupRequest req, Integer regMemberSeq, String regUserId) {
    // 1) 사용자 저장 (기존 signup 메서드)
    signup(req, regMemberSeq, regUserId);

    // 2) grants 가 있으면 권한 부여
    if (req.getGrants() != null) {
      for (GrantDto g : req.getGrants()) {
        // signupRequest.userId 를 granteeUserId 로 채워 줌
        g.setGranteeUserId(req.getUserId());
        // 권한 부여
        permissionService.grant(g);
      }
    }
  }

  /**
   * 로그인
   */
  @Override
  public TmemberEntity login(LoginRequest req) {
    TmemberEntity user = repo.findByUserId(req.getUserId())
        .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 틀립니다."));
    if (user.getDiscd() != null && user.getDiscd() == 1) {
      throw new IllegalStateException("비활성 사용자입니다.");
    }
    if (!passwordEncoder.matches(req.getUserPass(), user.getUserPass())) {
      throw new IllegalArgumentException("아이디 또는 비밀번호가 틀립니다.");
    }
    session.setAttribute("memberSeq", user.getMemberSeq());
    return user;
  }

  /**
   * 로그아웃(세션 무효화)
   */
  @Override
  public void logout() {
    session.invalidate();
  }

  /**
   * 세션 기반 내 정보 조회
   */
  @Override
  public Info getMyInfo() {
    Integer me = (Integer) session.getAttribute("memberSeq");
    if (me == null) {
      throw new IllegalStateException("로그인 상태가 아닙니다.");
    }
    TmemberEntity e = repo.findById(me)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    Info dto = Info.fromEntity(e);

    // ★ branchSeq > 0 인 경우에만 지점명 조회
    if (dto.getBranchSeq() != null && dto.getBranchSeq() > 0) {
      TbranchDto b = branchSvc.findById(dto.getBranchSeq());
      dto.setBranchName(b.getCompanyName());
    }
    return dto;
  }

  @Override
  public Info getMyInfoByMemberSeq(Integer memberSeq) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + memberSeq));
    return Info.fromEntity(e);
  }

  /**
   * 토큰(userId) 기반 내 정보 조회
   */
  @Override
  public Info getMyInfoByUserId(String userId) {
    TmemberEntity e = repo.findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    Info dto = Info.fromEntity(e);

    if (dto.getBranchSeq() != null && dto.getBranchSeq() > 0) {
      TbranchDto b = branchSvc.findById(dto.getBranchSeq());
      dto.setBranchName(b.getCompanyName());
    }
    return dto;
  }

  /**
   * 비밀번호 변경
   */
  @Override
  @Transactional
  public void changePassword(Integer memberSeq, PasswordChangeRequest req) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    if (!passwordEncoder.matches(req.getOldPassword(), e.getUserPass())) {
      throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
    }
    e.setUserPass(passwordEncoder.encode(req.getNewPassword()));
    repo.save(e);
  }

  /**
   * 페이징된 전체 유저 조회
   */
  @Override
  public Page<Info> listAllUsers(Pageable pageable) {
    return repo.findAll(pageable)
        .map(e -> {
          Info dto = Info.fromEntity(e);
          // ★ 여기에도 반드시 > 0 체크
          if (dto.getBranchSeq() != null && dto.getBranchSeq() > 0) {
            TbranchDto b = branchSvc.findById(dto.getBranchSeq());
            dto.setBranchName(b.getCompanyName());
          }
          return dto;
        });
  }

  // ★ 새로 추가: 키워드 통합 검색
  @Override
  public Page<Info> searchUsers(String keyword, Pageable pageable) {
    return memberRepo
        .findByUserIdContainingOrNumberContaining(keyword, keyword, pageable)
        .map(entity -> {
          Info dto = Info.fromEntity(entity);
          if (dto.getBranchSeq()!=null && dto.getBranchSeq()>0) {
            TbranchDto b = branchSvc.findById(dto.getBranchSeq());
            dto.setBranchName(b.getCompanyName());
          }
          return dto;
        });
  }



  /**
   * 활성/비활성 상태 변경
   */
  @Override
  @Transactional
  public void changeStatus(Integer memberSeq, StatusChangeRequest req) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    e.setDiscd(req.isActive() ? 0 : 1);
    repo.save(e);
  }

  /**
   * 역할 번호 조회
   */
  @Override
  public Integer getRoleSeqOf(Integer memberSeq) {
    return repo.findById(memberSeq)
        .orElseThrow(() -> new IllegalStateException("사용자 없음: " + memberSeq))
        .getRoleSeq();
  }

  /**
   * 역할 변경
   */
  @Override
  @Transactional
  public void changeRole(Integer memberSeq, Integer newRoleSeq) {
    int updated = repo.updateRole(memberSeq, newRoleSeq);
    if (updated == 0) {
      throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + memberSeq);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<Info> getAllMembers() {
    return memberRepo.findAll().stream()
        .map(e -> Info.builder()
            .memberSeq(e.getMemberSeq())
            .userId(e.getUserId())
            .number(e.getNumber())
            .build()
        )
        .collect(Collectors.toList());
  }


  @Override
  public Integer getMemberSeqByNumber(String number) {
    // 1) 가능한 후보 번호 목록 준비
    List<String> candidates = new ArrayList<>();
    candidates.add(number);  // 원본 그대로

    // 2) leading zero 제거
    String noLeading = number.replaceFirst("^0+", "");
    if (!noLeading.equals(number)) {
      candidates.add(noLeading);
    }

    // 3) 4자리 패딩 (예: "334" → "0334")
    try {
      int num = Integer.parseInt(noLeading);
      String padded = String.format("%04d", num);
      if (!candidates.contains(padded)) {
        candidates.add(padded);
      }
    } catch (NumberFormatException ignored) {
      // 숫자가 아닌 경우 무시
    }

    // 4) 후보들 순서대로 조회
    for (String cand : candidates) {
      Optional<TmemberEntity> opt = memberRepo.findByNumber(cand);
      if (opt.isPresent()) {
        return opt.get().getMemberSeq();
      }
    }

    // 모두 실패 시 예외
    throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "사용자를 찾을 수 없습니다 (number=" + number + ")"
    );
  }
}
