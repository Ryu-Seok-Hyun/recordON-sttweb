package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class TmemberServiceImpl implements TmemberService {

  private final TmemberRepository repo;
  private final PasswordEncoder passwordEncoder;
  private final HttpSession session;
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** 회원가입 */
  @Override
  @Transactional
  public void signup(SignupRequest req) {
    repo.findByUserId(req.getUserId())
        .ifPresent(u -> { throw new IllegalArgumentException("이미 존재하는 사용자 ID입니다."); });

    Integer meSeq = (Integer) session.getAttribute("memberSeq");
    if (meSeq == null) {
      throw new IllegalStateException("로그인 상태가 아닙니다.");
    }
    TmemberEntity me = repo.findById(meSeq)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    String regUserId = me.getUserId();

    TmemberEntity e = new TmemberEntity();
    e.setUserId(req.getUserId());
    e.setUserPass(passwordEncoder.encode(req.getUserPass()));
    e.setBranchSeq(req.getBranchSeq());
    e.setEmployeeId(req.getEmployeeId());
    e.setNumber(req.getNumber());
    e.setUserLevel("0".equals(req.getUserLevel()) ? "0" : "1");
    Integer r = req.getRoleSeq();
    e.setRoleSeq(r != null && r >= 1 && r <= 3 ? r : 1);

    String now = LocalDateTime.now().format(FMT);
    e.setCrtime(now);
    e.setUdtime(now);
    e.setReguserId(regUserId);

    repo.save(e);
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

  /** 세션 기반 내 정보 조회 */
  @Override
  public Info getMyInfo() {
    Integer me = (Integer) session.getAttribute("memberSeq");
    if (me == null) {
      throw new IllegalStateException("로그인 상태가 아닙니다.");
    }
    TmemberEntity e = repo.findById(me)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    return Info.fromEntity(e);
  }

  /** 토큰(userId) 기반 내 정보 조회 */
  @Override
  public Info getMyInfoByUserId(String userId) {
    TmemberEntity e = repo.findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    return Info.fromEntity(e);
  }

  /** 비밀번호 변경 */
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

  /** 페이징된 전체 유저 조회 */
  @Override
  public Page<Info> listAllUsers(Pageable pageable) {
    return repo.findAll(pageable)
        .map(Info::fromEntity);
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
}
