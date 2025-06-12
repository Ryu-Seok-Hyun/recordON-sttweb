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
    // signupWithGrants 메서드 삭제!

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

    // 추가: 비밀번호 초기화
    void resetPassword(Integer memberSeq, String rawPassword, String operatorId);
    void resetPasswords(List<Integer> memberSeqs, String rawPassword, String operatorId);
    void resetAllPasswords(String rawPassword, String operatorId);

    // 추가: branchSeq → branchName 조회
    String getBranchNameBySeq(Integer branchSeq);

    // 추가: 지점명으로 검색
    Page<Info> searchUsersByBranchName(String branchName, Pageable pageable);

    TmemberEntity findEntityByUserId(String userId);

    // 본사 관리자: 지점명 + 키워드 검색
    Page<Info> searchUsersByBranchNameAndKeyword(String branchName, String keyword, Pageable pageable);

    /**
     * 회원(memberSeq)의 마스킹 여부를 변경합니다.
     * @param memberSeq 변경 대상 회원의 PK
     * @param maskFlag 0=마스킹 활성, 1=마스킹 비활성
     */
    void updateMaskFlag(Integer memberSeq, Integer maskFlag);

    public Integer getMemberSeqByUserId(String userId);
    TmemberEntity findByMemberSeq(Integer memberSeq);
}
