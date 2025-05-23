// src/main/java/com/sttweb/sttweb/service/TmemberService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.UpdateRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TmemberService {

    void signup(SignupRequest req, Integer regMemberSeq, String regUserId);
    void signupWithGrants(SignupRequest req, Integer regMemberSeq, String regUserId);
    TmemberEntity login(LoginRequest req);
    void logout();
    Info getMyInfoByUserId(String userId);

    // 본사 관리자 전용
    Page<Info> searchUsers(String keyword, Pageable pageable);
    Page<Info> listAllUsers(Pageable pageable);

    void changeStatus(Integer memberSeq, StatusChangeRequest req);
    Integer getRoleSeqOf(Integer memberSeq);
    void changeRole(Integer memberSeq, Integer newRoleSeq);

    Info getMyInfoByMemberSeq(Integer memberSeq);
    List<Info> getAllMembers();

    // 지사 관리자 전용
    Page<Info> listUsersInBranch(Integer branchSeq, Pageable pageable);
    Page<Info> searchUsersInBranch(String keyword, Integer branchSeq, Pageable pageable);

    boolean existsUserInBranch(String userId, Integer branchSeq);
    boolean existsByUserId(String userId);

    Info updateMemberInfo(Integer memberSeq, UpdateRequest req, Integer updaterSeq, String updaterId);
    Integer getMemberSeqByNumber(String number);
    Info getInfoByMemberSeq(Integer memberSeq);

    void changePassword(Integer memberSeq, String oldPassword, String newPassword);

    // 🔥 추가: 비밀번호 초기화
    void resetPassword(Integer memberSeq, String rawPassword, String operatorId);

    // 🔥 추가: branchSeq → branchName 조회
    String getBranchNameBySeq(Integer branchSeq);

    void resetPasswords(List<Integer> memberSeqs, String rawPassword, String operatorId);

    /** 전체 초기화 */
    void resetAllPasswords(String rawPassword, String operatorId);
}
