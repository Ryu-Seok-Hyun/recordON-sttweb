package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TrecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 녹취 데이터 리포지토리
 * JpaRepository와 JpaSpecificationExecutor를 상속하여
 * 기본 CRUD 및 동적 쿼리 기능 제공
 */
@Repository
public interface TrecordRepository extends JpaRepository<TrecordEntity, Integer>,
    JpaSpecificationExecutor<TrecordEntity> {

  // ─────────────────────────────────────────────────────────────
  // 기본 검색 메서드들
  // ─────────────────────────────────────────────────────────────

  /**
   * number1으로 녹취 검색
   */
  Page<TrecordEntity> findByNumber1(String number1, Pageable pageable);

  /**
   * number2로 녹취 검색
   */
  Page<TrecordEntity> findByNumber2(String number2, Pageable pageable);

  /**
   * number1 또는 number2로 녹취 검색
   */
  Page<TrecordEntity> findByNumber1OrNumber2(String number1, String number2, Pageable pageable);

  /**
   * number1이 목록에 포함되거나 number2가 목록에 포함된 녹취 검색
   */
  Page<TrecordEntity> findByNumber1InOrNumber2In(List<String> number1List, List<String> number2List, Pageable pageable);

  // ─────────────────────────────────────────────────────────────
  // 지점별 검색 메서드들
  // ─────────────────────────────────────────────────────────────

  /**
   * 지점별 전체 녹취 조회
   */
  Page<TrecordEntity> findAllByBranchSeq(Integer branchSeq, Pageable pageable);

  /**
   * 지점별 내선번호 목록으로 녹취 검색
   */
  @Query("SELECT t FROM TrecordEntity t WHERE t.branchSeq = :branchSeq " +
      "AND (t.number1 IN :extensions OR t.number2 IN :extensions)")
  Page<TrecordEntity> findByBranchAndExtensions(@Param("branchSeq") Integer branchSeq,
      @Param("extensions") List<String> extensions,
      Pageable pageable);

  // ─────────────────────────────────────────────────────────────
  // 카운트 메서드들
  // ─────────────────────────────────────────────────────────────

  /**
   * 지점별 녹취 개수 조회
   */
  long countByBranchSeq(Integer branchSeq);

  /**
   * 통화 방향별 녹취 개수 조회
   */
  long countByIoDiscdVal(String ioDiscdVal);

  /**
   * 지점별, 통화 방향별 녹취 개수 조회
   */
  long countByBranchSeqAndIoDiscdVal(Integer branchSeq, String ioDiscdVal);

  // ─────────────────────────────────────────────────────────────
  // 복합 검색 메서드 (네이티브 쿼리 또는 JPQL 사용)
  // ─────────────────────────────────────────────────────────────

  /**
   * 복합 조건으로 녹취 검색
   * @param number1 첫 번째 번호
   * @param number2 두 번째 번호
   * @param inbound 수신 여부 (true: 수신, false: 발신, null: 전체)
   * @param isExt 내선번호 여부 (true: 내선, false: 외부번호, null: 전체)
   * @param query 검색어
   * @param start 시작 시간
   * @param end 종료 시간
   * @param pageable 페이징 정보
   * @return 검색 결과
   */
  @Query("SELECT t FROM TrecordEntity t WHERE " +
      "(:number1 IS NULL OR t.number1 LIKE %:number1%) AND " +
      "(:number2 IS NULL OR t.number2 LIKE %:number2%) AND " +
      "(:inbound IS NULL OR " +
      "  (:inbound = true AND t.ioDiscdVal = '수신') OR " +
      "  (:inbound = false AND t.ioDiscdVal = '발신')) AND " +
      "(:isExt IS NULL OR " +
      "  (:isExt = true AND LENGTH(t.number1) <= 4) OR " +
      "  (:isExt = false AND LENGTH(t.number1) > 4)) AND " +
      "(:query IS NULL OR t.callStatus LIKE %:query%) AND " +
      "(:start IS NULL OR t.callStartDateTime >= :start) AND " +
      "(:end IS NULL OR t.callStartDateTime <= :end)")
  Page<TrecordEntity> search(@Param("number1") String number1,
      @Param("number2") String number2,
      @Param("inbound") Boolean inbound,
      @Param("isExt") Boolean isExt,
      @Param("query") String query,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      Pageable pageable);

  // ─────────────────────────────────────────────────────────────
  // 추가 유틸리티 메서드들
  // ─────────────────────────────────────────────────────────────

  /**
   * 특정 날짜 범위의 녹취 조회
   */
  @Query("SELECT t FROM TrecordEntity t WHERE " +
      "t.callStartDateTime BETWEEN :start AND :end")
  Page<TrecordEntity> findByDateRange(@Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      Pageable pageable);

  /**
   * 최근 녹취 조회 (등록일 기준)
   */
  @Query("SELECT t FROM TrecordEntity t ORDER BY t.regDate DESC")
  Page<TrecordEntity> findRecentRecords(Pageable pageable);

  /**
   * 통화 상태별 녹취 조회
   */
  Page<TrecordEntity> findByCallStatus(String callStatus, Pageable pageable);

  /**
   * 지점 및 날짜 범위로 녹취 조회
   */
  @Query("SELECT t FROM TrecordEntity t WHERE " +
      "t.branchSeq = :branchSeq AND " +
      "t.callStartDateTime BETWEEN :start AND :end")
  Page<TrecordEntity> findByBranchAndDateRange(@Param("branchSeq") Integer branchSeq,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      Pageable pageable);

  /**
   * 소유자별 녹취 조회
   */
  Page<TrecordEntity> findByOwnerMemberSeq(Integer ownerMemberSeq, Pageable pageable);

  /**
   * 지점별 소유자별 녹취 조회
   */
  Page<TrecordEntity> findByBranchSeqAndOwnerMemberSeq(Integer branchSeq,
      Integer ownerMemberSeq,
      Pageable pageable);

  // ─────────────────────────────────────────────────────────────
  // 통계용 메서드들
  // ─────────────────────────────────────────────────────────────

  /**
   * 지점별 일별 녹취 통계
   */
  @Query("SELECT DATE(t.callStartDateTime) as callDate, COUNT(t) as recordCount " +
      "FROM TrecordEntity t WHERE t.branchSeq = :branchSeq " +
      "AND t.callStartDateTime BETWEEN :start AND :end " +
      "GROUP BY DATE(t.callStartDateTime) " +
      "ORDER BY DATE(t.callStartDateTime)")
  List<Object[]> getDailyStatsByBranch(@Param("branchSeq") Integer branchSeq,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * 시간대별 녹취 통계
   */
  @Query("SELECT HOUR(t.callStartDateTime) as callHour, COUNT(t) as recordCount " +
      "FROM TrecordEntity t WHERE " +
      "t.callStartDateTime BETWEEN :start AND :end " +
      "GROUP BY HOUR(t.callStartDateTime) " +
      "ORDER BY HOUR(t.callStartDateTime)")
  List<Object[]> getHourlyStats(@Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * 방향별 통계 (지점별)
   */
  @Query("SELECT t.ioDiscdVal, COUNT(t) " +
      "FROM TrecordEntity t WHERE t.branchSeq = :branchSeq " +
      "GROUP BY t.ioDiscdVal")
  List<Object[]> getDirectionStatsByBranch(@Param("branchSeq") Integer branchSeq);

  /**
   * 방향별 통계 (전체)
   */
  @Query("SELECT t.ioDiscdVal, COUNT(t) " +
      "FROM TrecordEntity t " +
      "GROUP BY t.ioDiscdVal")
  List<Object[]> getDirectionStatsAll();

  // TrecordRepository.java
  @Query("SELECT t FROM TrecordEntity t WHERE " +
      "(t.branchSeq = :branchSeq AND (t.number1 IN :extensions OR t.number2 IN :extensions)) " +
      "OR (t.number1 = :myExtension OR t.number2 = :myExtension)")
  Page<TrecordEntity> findByBranchAndExtensionsOrMyNumber(
      @Param("branchSeq") Integer branchSeq,
      @Param("extensions") List<String> extensions,
      @Param("myExtension") String myExtension,
      Pageable pageable
  );

  @Query("SELECT t FROM TrecordEntity t WHERE " +
      "(t.branchSeq = :branchSeq AND (t.number1 IN :numbers OR t.number2 IN :numbers)) " +
      "OR (t.number1 IN :numbers OR t.number2 IN :numbers)")
  Page<TrecordEntity> findByBranchAndExtensionsOrNumberOnly(
      @Param("branchSeq") Integer branchSeq,
      @Param("numbers") List<String> numbers,
      Pageable pageable
  );


  @Query("""
      select t from TrecordEntity t
       where (t.number1 in :nums or t.number2 in :nums)
         and (
           :direction = 'ALL'
           or (:direction = 'IN'  and t.ioDiscdVal = '수신')
           or (:direction = 'OUT' and t.ioDiscdVal = '발신')
         )
         and (
           :numberKind = 'ALL'
           or (:numberKind = 'EXT'   and length(t.number1) <= 4)
           or (:numberKind = 'PHONE' and length(t.number1) > 4)
         )
         and (:start is null or t.callStartDateTime >= :start)
         and (:end   is null or t.callStartDateTime <= :end)
         and (:q     is null or t.callStatus like concat('%', :q, '%'))
    """)
  Page<TrecordEntity> findByMixedFilter(
      @Param("nums")       List<String>  nums,
      @Param("direction")  String        direction,
      @Param("numberKind") String        numberKind,
      @Param("q")          String        q,
      @Param("start")      LocalDateTime start,
      @Param("end")        LocalDateTime end,
      Pageable            pageable
  );
}
