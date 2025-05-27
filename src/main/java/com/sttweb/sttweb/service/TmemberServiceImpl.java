package com.sttweb.sttweb.service.impl;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.UpdateRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.exception.ResourceNotFoundException;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.service.PermissionService;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
  private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*\\d)(?=.*\\W).{8,}$";

  @Override
  public boolean existsByUserId(String userId) {
    return repo.existsByUserId(userId);
  }

  @Override
  public boolean existsUserInBranch(String userId, Integer branchSeq) {
    if (branchSeq == null) return false;
    return repo.countByUserIdAndBranchSeq(userId, branchSeq) > 0;
  }

  /** 회원가입 */
  @Override
  @Transactional
  public void signup(SignupRequest req, Integer regMemberSeq, String regUserId) {
    // 1) 등록자 권한 확인
    TmemberEntity creator = repo.findById(regMemberSeq)
        .orElseThrow(() -> new ResourceNotFoundException("등록자 정보를 찾을 수 없습니다"));
    String creatorLevel = creator.getUserLevel();
    if (!"0".equals(creatorLevel) && !"1".equals(creatorLevel)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "가입 권한이 없습니다.");
    }

    // 2) 전역 중복 검사
    if (repo.existsByUserId(req.getUserId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 ID 입니다.");
    }

    // 3) userLevel / branchSeq 검증
    String level = req.getUserLevel();
    if ("0".equals(level)) {
      if (!"0".equals(creatorLevel)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 생성 가능합니다.");
      }
      req.setBranchSeq(null);
    } else {
      if (req.getBranchSeq() == null || branchSvc.findById(req.getBranchSeq()) == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 지사번호를 지정하세요.");
      }
      if ("1".equals(level) && !"0".equals(creatorLevel)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 지사 관리자 생성 가능합니다.");
      }
    }

    // 4) 비밀번호 정책 검사
    String rawPass = req.getUserPass();
    if (!rawPass.matches(PASSWORD_PATTERN)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "비밀번호는 최소 8자 이상이며, 영어 소문자·숫자·특수문자를 포함해야 합니다.");
    }

    // 5) 해시 및 엔티티 생성
    String hashPass = passwordEncoder.encode(rawPass);
    TmemberEntity e = new TmemberEntity();
    e.setUserId(req.getUserId());
    e.setUserPass(hashPass);
    e.setBranchSeq(req.getBranchSeq());
    e.setEmployeeId(req.getEmployeeId());
    e.setNumber(req.getNumber());
    e.setUserLevel(level);
    e.setRoleSeq(Optional.ofNullable(req.getRoleSeq()).filter(r -> r >= 1 && r <= 4).orElse(1));
    String now = LocalDateTime.now().format(FMT);
    e.setCrtime(now);
    e.setUdtime(now);
    e.setReguserId(regUserId);

    e.setPosition(StringUtils.hasText(req.getPosition())
        ? req.getPosition().trim() : null);
    e.setRank(StringUtils.hasText(req.getRank())
        ? req.getRank().trim() : null);
    e.setDepartment(StringUtils.hasText(req.getDepartment())
        ? req.getDepartment().trim() : null);

    repo.save(e);
  }

  /** 회원가입 + 권한 부여 */
  @Override
  @Transactional
  public void signupWithGrants(SignupRequest req, Integer regMemberSeq, String regUserId) {
    signup(req, regMemberSeq, regUserId);
    if (req.getGrants() != null) {
      for (GrantDto g : req.getGrants()) {
        g.setGranteeUserId(req.getUserId());
        permissionService.grant(g);
      }
    }
  }

  /** 로그인 */
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

  /** 로그아웃 */
  @Override
  public void logout() {
    session.invalidate();
  }

  /** branchSeq → branchName 조회 */
  @Override
  public String getBranchNameBySeq(Integer branchSeq) {
    if (branchSeq == null || branchSeq <= 0) return null;
    TbranchDto b = branchSvc.findById(branchSeq);
    return (b != null) ? b.getCompanyName() : null;
  }

  /** 내 정보 조회 (memberSeq) */
  @Override
  public Info getMyInfoByMemberSeq(Integer memberSeq) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + memberSeq));
    Info dto = Info.fromEntity(e);
    dto.setBranchName(getBranchNameBySeq(dto.getBranchSeq()));
    dto.setPosition(e.getPosition());
    dto.setRank(e.getRank());
    dto.setDepartment(e.getDepartment());
    return dto;
  }

  /** 회원 상세조회 */
  @Override
  public Info getInfoByMemberSeq(Integer memberSeq) {
    return getMyInfoByMemberSeq(memberSeq);
  }

  /** 내 정보 조회 (userId) */
  @Override
  public Info getMyInfoByUserId(String userId) {
    TmemberEntity e = repo.findByUserId(userId)
        .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
    Info dto = Info.fromEntity(e);
    dto.setBranchName(getBranchNameBySeq(dto.getBranchSeq()));
    dto.setPosition(e.getPosition());
    dto.setRank(e.getRank());
    dto.setDepartment(e.getDepartment());
    return dto;
  }

  /** 본사 관리자: 전체 유저 페이징 조회 */
  @Override
  public Page<Info> listAllUsers(Pageable pageable) {
    return repo.findAll(pageable)
        .map(this::toDtoWithBranchNameAndPosition);
  }

  /** 본사 관리자: 키워드 검색 */
  @Override
  public Page<Info> searchUsers(String keyword, Pageable pageable) {
    return repo.findByUserIdContainingOrNumberContaining(keyword, keyword, pageable)
        .map(this::toDtoWithBranchNameAndPosition);
  }

  /** 지사 관리자: 해당 지사 페이징 조회 */
  @Override
  public Page<Info> listUsersInBranch(Integer branchSeq, Pageable pageable) {
    return repo.findByBranchSeq(branchSeq, pageable)
        .map(this::toDtoWithBranchNameAndPosition);
  }

  /** 지사 관리자: 지사+키워드 검색 */
  @Override
  public Page<Info> searchUsersInBranch(String keyword, Integer branchSeq, Pageable pageable) {
    return repo.findByBranchSeqAnd(branchSeq, keyword, pageable)
        .map(this::toDtoWithBranchNameAndPosition);
  }

  @Override
  public Page<Info> searchUsersByBranchName(String branchName, Pageable pageable) {
    return repo.findByBranchNameContaining(branchName, pageable)
        .map(this::toDtoWithBranchNameAndPosition);
  }

  private Info toDtoWithBranchNameAndPosition(TmemberEntity e) {
    Info dto = Info.fromEntity(e);
    dto.setBranchName(getBranchNameBySeq(dto.getBranchSeq()));
    dto.setPosition(e.getPosition());
    dto.setRank(e.getRank());
    dto.setDepartment(e.getDepartment());
    return dto;
  }

  /** 상태 변경 */
  @Override
  @Transactional
  public void changeStatus(Integer memberSeq, StatusChangeRequest req) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
    e.setDiscd(req.isActive() ? 0 : 1);
    repo.save(e);
  }

  /** 역할 Seq 조회 */
  @Override
  public Integer getRoleSeqOf(Integer memberSeq) {
    return repo.findById(memberSeq)
        .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + memberSeq))
        .getRoleSeq();
  }

  /** 역할 변경 */
  @Override
  @Transactional
  public void changeRole(Integer memberSeq, Integer newRoleSeq) {
    int updated = repo.updateRole(memberSeq, newRoleSeq);
    if (updated == 0) {
      throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + memberSeq);
    }
  }

  /** 모든 멤버 목록 */
  @Override
  public List<Info> getAllMembers() {
    return repo.findAll().stream()
        .map(e -> Info.builder()
            .memberSeq(e.getMemberSeq())
            .userId(e.getUserId())
            .number(e.getNumber())
            .position(e.getPosition())
            .rank(e.getRank())
            .department(e.getDepartment())
            .build())
        .collect(Collectors.toList());
  }

  /** 회원정보 수정 */
  @Override
  @Transactional
  public Info updateMemberInfo(Integer memberSeq, UpdateRequest req, Integer updaterSeq, String updaterId) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대상 사용자가 없습니다: " + memberSeq));

    e.setNumber(req.getNumber());
    if (req.getEmployeeId() != null) e.setEmployeeId(req.getEmployeeId());
    if (req.getBranchSeq() != null) e.setBranchSeq(req.getBranchSeq());
    if (req.getRoleSeq() != null) e.setRoleSeq(req.getRoleSeq());
    if (req.getUserLevel() != null) e.setUserLevel(req.getUserLevel());
    if (req.getActive() != null) e.setDiscd(req.getActive() ? 0 : 1);


    if (req.getPosition() != null) {
      e.setPosition(StringUtils.hasText(req.getPosition())
          ? req.getPosition().trim() : null);
    }
    if (req.getRank() != null) {
      e.setRank(StringUtils.hasText(req.getRank())
          ? req.getRank().trim() : null);
    }
    if (req.getDepartment() != null) {
      e.setDepartment(StringUtils.hasText(req.getDepartment())
          ? req.getDepartment().trim() : null);
    }
    e.setReguserId(updaterId);
    e.setUdtime(LocalDateTime.now().format(FMT));
    TmemberEntity saved = repo.save(e);

    Info dto = Info.fromEntity(saved);
    dto.setBranchName(getBranchNameBySeq(dto.getBranchSeq()));
    dto.setPosition(saved.getPosition());
    dto.setRank(saved.getRank());
    dto.setDepartment(saved.getDepartment());
    return dto;
  }

  /** 내 비밀번호 변경 */
  @Override
  @Transactional
  public void changePassword(Integer memberSeq, String oldPassword, String newPassword) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + memberSeq));

    // 1) 현재 비밀번호 검증
    if (!passwordEncoder.matches(oldPassword, e.getUserPass())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
    }
    // 2) 정책 검증
    if (!newPassword.matches(PASSWORD_PATTERN)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "새 비밀번호는 최소 8자 이상이며, 영어 소문자·숫자·특수문자를 포함해야 합니다.");
    }
    // 3) 변경
    e.setUserPass(passwordEncoder.encode(newPassword));
    repo.save(e);
  }


  /** memberSeq 조회 (number 기반) */
  @Override
  public Integer getMemberSeqByNumber(String number) {
    List<String> candidates = new ArrayList<>();
    candidates.add(number);
    String noLeading = number.replaceFirst("^0+", "");
    if (!noLeading.equals(number)) candidates.add(noLeading);
    try {
      int num = Integer.parseInt(noLeading);
      String padded = String.format("%04d", num);
      if (!candidates.contains(padded)) candidates.add(padded);
    } catch (NumberFormatException ignored) {
    }
    for (String cand : candidates) {
      Optional<TmemberEntity> opt = repo.findByNumber(cand);
      if (opt.isPresent()) {
        return opt.get().getMemberSeq();
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
        "사용자를 찾을 수 없습니다 (number=" + number + ")");
  }

  /** 단일 사용자 비밀번호 초기화 */
  @Override
  @Transactional
  public void resetPassword(Integer memberSeq, String rawPassword, String operatorId) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + memberSeq));
    e.setUserPass(passwordEncoder.encode(rawPassword));
    repo.save(e);
  }

  /** 비밀번호 초기화 */
  @Override
  @Transactional
  public void resetPasswords(List<Integer> memberSeqs, String rawPassword, String operatorId) {
    for (Integer seq : memberSeqs) {
      TmemberEntity e = repo.findById(seq)
          .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + seq));
      e.setUserPass(passwordEncoder.encode(rawPassword));
      repo.save(e);
    }
  }

  @Override
  @Transactional
  public void resetAllPasswords(String rawPassword, String operatorId) {
    List<TmemberEntity> all = repo.findAll();
    for (TmemberEntity e : all) {
      e.setUserPass(passwordEncoder.encode(rawPassword));
    }
    repo.saveAll(all);
  }

  /**
   * DB에서 userId로 엔티티 조회 (재인증용)
   */
  @Override
  @Transactional(readOnly = true)
  public TmemberEntity findEntityByUserId(String userId) {
    return memberRepo.findByUserId(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
  }
}
