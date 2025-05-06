package com.sttweb.sttweb.service;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.repository.TmemberRepository;
import com.sttweb.sttweb.service.TmemberService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TmemberServiceImpl implements TmemberService {
  private final TmemberRepository repo;
  private final PasswordEncoder passwordEncoder;
  private final HttpSession session;
//  private final JwtTokenProvider jwtTokenProvider;
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  @Transactional
  public void signup(SignupRequest req) {
    // 1) 중복 체크
    repo.findByUserId(req.getUserId())
        .ifPresent(u -> { throw new IllegalArgumentException("이미 존재하는 사용자 ID입니다."); });

    // 2) 세션에서 내 memberSeq 꺼내기
    Integer meSeq = (Integer) session.getAttribute("memberSeq");
    if (meSeq == null) {
      throw new IllegalStateException("로그인 상태가 아닙니다.");
    }
    TmemberEntity me = repo.findById(meSeq)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    String regUserId = me.getUserId();

    // 3) 신규 회원 엔티티 생성
    TmemberEntity e = new TmemberEntity();
    e.setUserId(req.getUserId());
    // 비밀번호 암호화
    e.setUserPass(passwordEncoder.encode(req.getUserPass()));
    e.setBranchSeq(req.getBranchSeq());
    e.setNumber(req.getNumber());

    // userLevel 기본값 처리: null 혹은 "1" 이면 "1", "0" 이면 "0"
    String level = "0".equals(req.getUserLevel()) ? "0" : "1";
    e.setUserLevel(level);

    String now = LocalDateTime.now().format(FMT);
    e.setCrtime(now);
    e.setUdtime(now);

    // reguserId 에 로그인한 내 userId 를 넣는다
    e.setReguserId(regUserId);

    repo.save(e);
  }




  @Override
  public TmemberEntity login(LoginRequest req) {
    // 1) 아이디로 사용자 조회
    TmemberEntity user = repo.findByUserId(req.getUserId())
        .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 틀립니다."));

    // 2) 비활성(discd=1) 계정인지 검사
    if (user.getDiscd() != null && user.getDiscd() == 1) {
      throw new IllegalStateException("비활성 사용자입니다.");
    }

    // 3) 비밀번호 체크
    if (!req.getUserPass().equals(user.getUserPass())) {
      throw new IllegalArgumentException("아이디 또는 비밀번호가 틀립니다.");
    }

    // 4) 로그인 성공
    session.setAttribute("memberSeq", user.getMemberSeq());
    return user;
  }


  @Override
  public void logout() {
    session.invalidate();
  }

  @Override
  public Info getMyInfo() {
    Integer me = (Integer) session.getAttribute("memberSeq");
    if (me == null) throw new IllegalStateException("로그인 상태가 아닙니다.");
    TmemberEntity e = repo.findById(me)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    return Info.fromEntity(e);
  }

  @Override
  public Info getMyInfoByUserId(String userId) {
    // userId 로만 조회
    TmemberEntity e = repo.findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    return Info.fromEntity(e);
  }

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

  @Override
  public List<Info> listAllUsers() {
    return repo.findAll().stream()
        .map(Info::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void changeStatus(Integer memberSeq, StatusChangeRequest req) {
    TmemberEntity e = repo.findById(memberSeq)
        .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));
    e.setDiscd(req.isActive() ? 0 : 1);
    repo.save(e);
  }
}