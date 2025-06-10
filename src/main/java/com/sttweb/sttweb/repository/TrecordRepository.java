package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TrecordEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrecordRepository
    extends JpaRepository<TrecordEntity, Integer>,
    JpaSpecificationExecutor<TrecordEntity> {

  // ─────────────────────────────────────────────────────────────
  // 1) 기본 CRUD & 번호별 페이징 조회 메서드
  // ─────────────────────────────────────────────────────────────
  Page<TrecordEntity> findByNumber1(String number1, Pageable pageable);
  Page<TrecordEntity> findByNumber2(String number2, Pageable pageable);
  Page<TrecordEntity> findByNumber1OrNumber2(String number1, String number2, Pageable pageable);

  /**
   * 다중 번호(equal) 검색
   */
  Page<TrecordEntity> findByNumber1InOrNumber2In(
      List<String> numbers1,
      List<String> numbers2,
      Pageable pageable
  );

  // ─────────────────────────────────────────────────────────────
  // 2) branchSeq 기준 페이징 조회 메서드
  //    (TrecordServiceImpl 내 findAllByBranch() 에서 사용)
  // ─────────────────────────────────────────────────────────────
  Page<TrecordEntity> findByBranchSeq(Integer branchSeq, Pageable pageable);

  // ─────────────────────────────────────────────────────────────
  // 3) 통합 검색(번호1, 번호2, IN/OUT, 내선/전화, 키워드, 시간 범위)용 @Query 메서드
  //
  //    서비스 코드에서 repo.search(...) 형태로 호출하게 됩니다.
  // ─────────────────────────────────────────────────────────────
  @Query("""
    select t
      from TrecordEntity t
     where (:num1    is null or t.number1 = :num1)
       and (:num2    is null or t.number2 = :num2)
       and (:inbound is null 
            or t.ioDiscdVal = case when :inbound = true then '수신' else '발신' end)
       and (
            :q is null
         or (
              (:isExt is null)
           and (t.callStatus   like concat('%', :q, '%')
             or t.number1      like concat('%', :q, '%')
             or t.number2      like concat('%', :q, '%'))
          )
         or (
              :isExt = true
           and (t.callStatus   like concat('%', :q, '%')
             or t.number1      like concat('%', :q, '%'))
          )
         or (
              :isExt = false
           and (t.callStatus   like concat('%', :q, '%')
             or t.number2      like concat('%', :q, '%'))
          )
       )
       and (:start is null or t.callStartDateTime >= :start)
       and (:end   is null or t.callEndDateTime   <= :end)
  """)
  Page<TrecordEntity> search(
      @Param("num1")    String        num1,
      @Param("num2")    String        num2,
      @Param("inbound") Boolean       inbound,
      @Param("isExt")   Boolean       isExt,
      @Param("q")       String        q,
      @Param("start")   LocalDateTime start,
      @Param("end")     LocalDateTime end,
      Pageable                    pageable
  );

  // ─────────────────────────────────────────────────────────────
  // 4) 지점별 조회 및 카운트 메서드
  // ─────────────────────────────────────────────────────────────

  // 1) 지점별 페이징 조회
  Page<TrecordEntity> findAllByBranchSeq(Integer branchSeq, Pageable pageable);

  // 2) 지점별 전체 건수(ALL)
  long countByBranchSeq(Integer branchSeq);

  // 3) 지점별 IN/OUT 카운트
  //    - DB 컬럼명이 ioDiscdVal 이라면 아래처럼
  long countByBranchSeqAndIoDiscdVal(Integer branchSeq, String ioDiscdVal);

  // ─────────────────────────────────────────────────────────────
  // 5) 내선번호 구분 검색 메서드 (NEW!)
  // ─────────────────────────────────────────────────────────────

  /**
   * 특정 지점의 내선번호(4자리)만 검색
   */
  @Query(value = """
    SELECT t.*
      FROM trecord t
     WHERE t.branch_seq = :branchSeq
       AND ((t.number1 IN :extensions AND LENGTH(t.number1) = 4)
         OR (t.number2 IN :extensions AND LENGTH(t.number2) = 4))
    """,
      countQuery = """
    SELECT COUNT(*)
      FROM trecord t
     WHERE t.branch_seq = :branchSeq
       AND ((t.number1 IN :extensions AND LENGTH(t.number1) = 4)
         OR (t.number2 IN :extensions AND LENGTH(t.number2) = 4))
    """,
      nativeQuery = true)
  Page<TrecordEntity> findByBranchAndExtensions(
      @Param("branchSeq") Integer branchSeq,
      @Param("extensions") List<String> extensions,
      Pageable pageable
  );

  /**
   * 내선번호와 전화번호를 구분해서 혼합 검색
   */
  @Query("""
    SELECT t FROM TrecordEntity t WHERE
    (
      (t.number1 IN :extensions AND LENGTH(t.number1) = 4) OR
      (t.number2 IN :extensions AND LENGTH(t.number2) = 4) OR
      (t.number1 IN :phones AND LENGTH(t.number1) != 4) OR
      (t.number2 IN :phones AND LENGTH(t.number2) != 4)
    )
    """)
  Page<TrecordEntity> findByExtensionsAndPhones(
      @Param("extensions") List<String> extensions,
      @Param("phones") List<String> phones,
      Pageable pageable
  );

  // 기존 내선번호(4자리)만 대상으로 IN 검색 (레거시 - 지점 구분 없음)
  @Query(value = """
    SELECT t.*
      FROM trecord t
     WHERE ((t.number1 IN :callNums AND LENGTH(t.number1) = 4)
         OR  (t.number2 IN :callNums AND LENGTH(t.number2) = 4))
    """,
      countQuery = """
    SELECT COUNT(*)
      FROM trecord t
     WHERE ((t.number1 IN :callNums AND LENGTH(t.number1) = 4)
         OR  (t.number2 IN :callNums AND LENGTH(t.number2) = 4))
    """,
      nativeQuery = true)
  Page<TrecordEntity> findByCallNumsWithFourLength(@Param("callNums") List<String> callNums, Pageable pageable);
}