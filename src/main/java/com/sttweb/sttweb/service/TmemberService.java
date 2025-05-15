package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TmemberService {
  void signup(SignupRequest req, Integer regMemberSeq, String regUserId);
  TmemberEntity login(LoginRequest req);
  void logout();
  Info getMyInfo();
  Info getMyInfoByUserId(String userId);
  void changePassword(Integer memberSeq, PasswordChangeRequest req);

  /** 페이징 전용 메서드 */
  Page<Info> listAllUsers(Pageable pageable);

  void changeStatus(Integer memberSeq, StatusChangeRequest req);
  Integer getRoleSeqOf(Integer memberSeq);
  void changeRole(Integer memberSeq, Integer newRoleSeq);

  // 권한관련
  Info getMyInfoByMemberSeq(Integer memberSeq);
  List<Info> getAllMembers();  // 전체 회원 목록을 Info DTO로 반환

}
