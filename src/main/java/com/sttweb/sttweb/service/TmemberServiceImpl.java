package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.UpdateRequest;
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
import org.springframework.util.StringUtils;
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


  /**
   * 동일 지점에 같은 userId 가 이미 있는지 여부
   */
  @Override
  public boolean existsUserInBranch(String userId, Integer branchSeq) {
    if (branchSeq == null) {
      return false;
    }
    // countByUserIdAndBranchSeq 는 0 이상일 때 중복이 있는 것
    return repo.countByUserIdAndBranchSeq(userId, branchSeq) > 0;
  }

  /** 전역 userId 중복 여부 */
  @Override
  public boolean existsByUserId(String userId) {
    return repo.existsByUserId(userId);
  }

  /**
   * 회원가입 (본사/지사 관리자 및 유저 구분, branchSeq 검증)
   */
  @Override
  @Transactional
  public void signup(SignupRequest req, Integer regMemberSeq, String regUserId) {
    // 1) 등록자 권한 확인
    TmemberEntity creator = repo.findById(regMemberSeq)
        .orElseThrow(() -> new IllegalStateException("등록자 정보를 찾을 수 없습니다"));
    String creatorLevel = creator.getUserLevel();
    if (!"0".equals(creatorLevel) && !"1".equals(creatorLevel)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "가입 권한이 없습니다.");
    }

    // 2) 지사 내 ID 중복 체크
    if (existsUserInBranch(req.getUserId(), req.getBranchSeq())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 ID 입니다.");
    }

    // 3) userLevel/branchSeq 검증
    String level = req.getUserLevel();
    if ("0".equals(level)) {
      if (!"0".equals(creatorLevel)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "본사 관리자는 본사 관리자만 생성할 수 있습니다.");
      }
      req.setBranchSeq(null);
    } else {
      if (req.getBranchSeq() == null || branchSvc.findById(req.getBranchSeq()) == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "유효한 지사 번호(branchSeq)를 지정하세요.");
      }
      if ("1".equals(level) && !"0".equals(creatorLevel)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "지사 관리자는 본사 관리자만 생성할 수 있습니다.");
      }
    }

    // 4) 엔티티 생성 및 저장
    TmemberEntity e = new TmemberEntity();
    e.setUserId(req.getUserId());
    e.setUserPass(passwordEncoder.encode(req.getUserPass()));
    e.setBranchSeq(req.getBranchSeq());
    e.setEmployeeId(req.getEmployeeId());
    e.setNumber(req.getNumber());
    e.setUserLevel(level);
    e.setRoleSeq(Optional.ofNullable(req.getRoleSeq()).filter(r -> r >= 1 && r <= 4).orElse(1));
    String now = LocalDateTime.now().format(FMT);
    e.setCrtime(now);
    e.setUdtime(now);
    e.setReguserId(regUserId);
    repo.save(e);
  }

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

  /** 로그아웃(세션 무효화) */
  @Override
  public void logout() {
    session.invalidate();
  }


  @Override
  public Info getMyInfoByMemberSeq(Integer memberSeq) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + memberSeq));
    return Info.fromEntity(e);
  }

  /** 토큰(userId) 기반 내 정보 조회 */
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


  /** 페이징된 전체 유저 조회 (본사 관리자 전용) */
  @Override
  public Page<Info> listAllUsers(Pageable pageable) {
    return repo.findAll(pageable)
        .map(this::toDtoWithBranchName);  // <<< 수정/추가됨 >>>
  }

  /** 키워드 통합 검색 (본사 관리자 전용) */
  @Override
  public Page<Info> searchUsers(String keyword, Pageable pageable) {
    return memberRepo
        .findByUserIdContainingOrNumberContaining(keyword, keyword, pageable)
        .map(this::toDtoWithBranchName);  // <<< 수정/추가됨 >>>
  }

  /** 지사 관리자: 해당 지점 사용자만 페이징 조회 */
  @Override
  public Page<Info> listUsersInBranch(Integer branchSeq, Pageable pageable) {  // <<< 수정/추가됨 >>>
    return repo.findByBranchSeq(branchSeq, pageable)
        .map(this::toDtoWithBranchName);
  }

  /** 지사 관리자: 키워드 + 지점 필터 검색 */
  @Override
  public Page<Info> searchUsersInBranch(String keyword, Integer branchSeq, Pageable pageable) {  // <<< 수정/추가됨 >>>
    return repo.findByBranchSeqAnd(branchSeq, keyword, pageable)
        .map(this::toDtoWithBranchName);
  }

  /** Entity → DTO + branchName 채워주는 헬퍼 */
  private Info toDtoWithBranchName(TmemberEntity e) {   // <<< 수정/추가됨 >>>
    Info dto = Info.fromEntity(e);
    if (dto.getBranchSeq() != null && dto.getBranchSeq() > 0) {
      TbranchDto b = branchSvc.findById(dto.getBranchSeq());
      dto.setBranchName(b.getCompanyName());
    }
    return dto;
  }

  /** 활성/비활성 상태 변경 */
  @Override
  @Transactional
  public void changeStatus(Integer memberSeq, StatusChangeRequest req) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    e.setDiscd(req.isActive() ? 0 : 1);
    repo.save(e);
  }

  /** 역할 번호 조회 */
  @Override
  public Integer getRoleSeqOf(Integer memberSeq) {
    return repo.findById(memberSeq)
        .orElseThrow(() -> new IllegalStateException("사용자 없음: " + memberSeq))
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
  @Transactional
  public Info updateMemberInfo(Integer memberSeq,
      UpdateRequest req,
      Integer updaterSeq,
      String updaterId) {

    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND,
                "대상 사용자가 없습니다: " + memberSeq));

    // 1) 기본 필드
    e.setNumber(req.getNumber());
    if (req.getEmployeeId() != null) {
      e.setEmployeeId(req.getEmployeeId());
    }
    if (req.getBranchSeq() != null) {
      branchSvc.findById(req.getBranchSeq());
      e.setBranchSeq(req.getBranchSeq());
    }
    if (req.getRoleSeq() != null) {
      e.setRoleSeq(req.getRoleSeq());
    }
    if (req.getUserLevel() != null) {
      e.setUserLevel(req.getUserLevel());
    }

    // 2) 활성/비활성 토글
    if (req.getActive() != null) {
      // active==true → discd=0, false → discd=1
      e.setDiscd(req.getActive() ? 0 : 1);
    }

    // ————— 비밀번호 변경 로직 —————
    boolean hasOld = StringUtils.hasText(req.getOldPassword());
    boolean hasNew = StringUtils.hasText(req.getNewPassword());

    // 둘 다 있어야만 비밀번호 변경 시도
    if (hasOld && hasNew) {
      // 1) 새 비밀번호 길이 체크
      if (req.getNewPassword().length() < 8) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "새 비밀번호는 8자 이상이어야 합니다.");
      }
      // 2) 기존 비밀번호 일치 체크
      if (!passwordEncoder.matches(req.getOldPassword(), e.getUserPass())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "현재 비밀번호가 일치하지 않습니다.");
      }
      // 3) 실제 변경
      e.setUserPass(passwordEncoder.encode(req.getNewPassword()));
    }
    // hasOld, hasNew 중 하나만 있거나 둘 다 없으면 → 무시하고 넘어감

    // (2) 수정자·수정시간 기록, 저장 후 DTO 변환...
    e.setReguserId(updaterId);
    e.setUdtime(LocalDateTime.now().format(FMT));
    TmemberEntity saved = repo.save(e);

    Info dto = Info.fromEntity(saved);
    if (dto.getBranchSeq() != null && dto.getBranchSeq() > 0) {
      dto.setBranchName(branchSvc.findById(dto.getBranchSeq()).getCompanyName());
    }
    return dto;
  }

  /**
   * 내선번호(number)로 여러 후보를 만들어서 조회하는 기존 로직 재사용.
   */
  @Override
  public Integer getMemberSeqByNumber(String number) {
    // 1) 가능한 후보 번호 목록 준비
    List<String> candidates = new ArrayList<>();
    candidates.add(number);

    // 2) leading zero 제거
    String noLeading = number.replaceFirst("^0+", "");
    if (!noLeading.equals(number)) {
      candidates.add(noLeading);
    }

    // 3) 4자리 패딩
    try {
      int num = Integer.parseInt(noLeading);
      String padded = String.format("%04d", num);
      if (!candidates.contains(padded)) {
        candidates.add(padded);
      }
    } catch (NumberFormatException ignored) {
    }

    // 4) 조회
    for (String cand : candidates) {
      Optional<TmemberEntity> opt = repo.findByNumber(cand);
      if (opt.isPresent()) {
        return opt.get().getMemberSeq();
      }
    }

    throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "사용자를 찾을 수 없습니다 (number=" + number + ")"
    );
  }

}
