package com.sttweb.sttweb.service;

import java.util.List;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.entity.TmemberEntity;

public interface TmemberService {
  void signup(SignupRequest req);
  TmemberEntity login(LoginRequest req);
  void logout();
  Info getMyInfo();                      // 기존 메서드
  Info getMyInfoByUserId(String userId); // 새로 추가할 메서드
  void changePassword(Integer memberSeq, PasswordChangeRequest req);
  List<Info> listAllUsers();
  void changeStatus(Integer memberSeq, StatusChangeRequest req);
}