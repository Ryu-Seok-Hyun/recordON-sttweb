package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TrecordEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TrecordRepository extends JpaRepository<TrecordEntity, Integer>,
    JpaSpecificationExecutor<TrecordEntity> {

  Page<TrecordEntity> findByNumber1(String number1, Pageable pageable);
  Page<TrecordEntity> findByNumber2(String number2, Pageable pageable);
  Page<TrecordEntity> findByNumber1OrNumber2(String number1, String number2, Pageable pageable);
  Page<TrecordEntity> findByNumber1InOrNumber2In(List<String> number1List, List<String> number2List, Pageable pageable);

  Page<TrecordEntity> findAllByBranchSeq(Integer branchSeq, Pageable pageable);

  @Query("SELECT t FROM TrecordEntity t WHERE t.branchSeq = :branchSeq AND (t.number1 IN :extensions OR t.number2 IN :extensions)")
  Page<TrecordEntity> findByBranchAndExtensions(@Param("branchSeq") Integer branchSeq,
      @Param("extensions") List<String> extensions,
      Pageable pageable);

  long countByBranchSeq(Integer branchSeq);
  long countByIoDiscdVal(String ioDiscdVal);
  long countByBranchSeqAndIoDiscdVal(Integer branchSeq, String ioDiscdVal);

  @Query("SELECT t FROM TrecordEntity t WHERE (:number1 IS NULL OR t.number1 LIKE %:number1%) AND (:number2 IS NULL OR t.number2 LIKE %:number2%) AND " +
      "(:inbound IS NULL OR ((:inbound = true AND t.ioDiscdVal = '수신') OR (:inbound = false AND t.ioDiscdVal = '발신'))) AND " +
      "(:isExt IS NULL OR ((:isExt = true AND LENGTH(t.number1) <= 4) OR (:isExt = false AND LENGTH(t.number1) > 4))) AND " +
      "(:query IS NULL OR t.callStatus LIKE %:query%) AND (:start IS NULL OR t.callStartDateTime >= :start) AND (:end IS NULL OR t.callStartDateTime <= :end)")
  Page<TrecordEntity> search(@Param("number1") String number1,
      @Param("number2") String number2,
      @Param("inbound") Boolean inbound,
      @Param("isExt") Boolean isExt,
      @Param("query") String query,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      Pageable pageable);

  @Query("SELECT t FROM TrecordEntity t WHERE t.callStartDateTime BETWEEN :start AND :end")
  Page<TrecordEntity> findByDateRange(@Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      Pageable pageable);

  @Query("SELECT t FROM TrecordEntity t ORDER BY t.regDate DESC")
  Page<TrecordEntity> findRecentRecords(Pageable pageable);

  Page<TrecordEntity> findByCallStatus(String callStatus, Pageable pageable);

  @Query("SELECT t FROM TrecordEntity t WHERE t.branchSeq = :branchSeq AND t.callStartDateTime BETWEEN :start AND :end")
  Page<TrecordEntity> findByBranchAndDateRange(@Param("branchSeq") Integer branchSeq,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      Pageable pageable);

  Page<TrecordEntity> findByOwnerMemberSeq(Integer ownerMemberSeq, Pageable pageable);
  Page<TrecordEntity> findByBranchSeqAndOwnerMemberSeq(Integer branchSeq, Integer ownerMemberSeq, Pageable pageable);

  @Query("""
    SELECT t FROM TrecordEntity t
     WHERE (:number1 IS NULL OR t.number1 LIKE CONCAT('%',:number1,'%'))
       AND (:number2 IS NULL OR t.number2 LIKE CONCAT('%',:number2,'%'))
       AND (:inbound IS NULL OR ((:inbound = true AND t.ioDiscdVal = '수신') OR (:inbound = false AND t.ioDiscdVal = '발신')))
       AND (:isExt IS NULL OR ((:isExt = true AND LENGTH(t.number1) <= 4) OR (:isExt = false AND LENGTH(t.number1) > 4)))
       AND (:q IS NULL OR t.number1 LIKE CONCAT('%',:q,'%') OR t.number2 LIKE CONCAT('%',:q,'%'))
       AND (:start IS NULL OR t.callStartDateTime >= :start)
       AND (:end IS NULL OR t.callStartDateTime <= :end)
  """)
  Page<TrecordEntity> searchByQuery(@Param("number1") String number1,
      @Param("number2") String number2,
      @Param("inbound") Boolean inbound,
      @Param("isExt") Boolean isExt,
      @Param("q") String q,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      Pageable pageable);

  @Query("""
    SELECT t FROM TrecordEntity t
     WHERE t.number1 IN :nums
       AND (:q IS NULL OR t.number1 LIKE CONCAT('%',:q,'%') OR t.number2 LIKE CONCAT('%',:q,'%'))
       AND (:direction = 'ALL' OR (:direction = 'IN' AND t.ioDiscdVal = '수신') OR (:direction = 'OUT' AND t.ioDiscdVal = '발신'))
       AND (:numberKind = 'ALL' OR (:numberKind = 'EXT' AND LENGTH(t.number1) <= 4) OR (:numberKind = 'PHONE' AND LENGTH(t.number1) > 4))
       AND (:start IS NULL OR t.callStartDateTime >= :start)
       AND (:end IS NULL OR t.callStartDateTime <= :end)
  """)
  Page<TrecordEntity> searchByNumsAndQuery(@Param("nums") List<String> nums,
      @Param("direction") String direction,
      @Param("numberKind") String numberKind,
      @Param("q") String q,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      Pageable pageable);

  Page<TrecordEntity> findByNumber1ContainingOrNumber2Containing(String number1, String number2, Pageable pageable);
  Page<TrecordEntity> findByNumber2Containing(String number2, Pageable pageable);

  @Query("""
      SELECT t.ioDiscdVal AS direction, COUNT(t) AS cnt
        FROM TrecordEntity t
       WHERE (:start IS NULL OR t.callStartDateTime >= :start)
         AND (:end   IS NULL OR t.callStartDateTime <= :end)
       GROUP BY t.ioDiscdVal
  """)
  List<Object[]> countByDirectionGrouped(@Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query("""
    SELECT t FROM TrecordEntity t
     WHERE LOWER(function('substring_index', function('replace', t.audioFileDir, '\\\\', '/'), '/', -1)) IN (:basenames)
       AND (t.audioPlayTime IS NULL OR function('time_to_sec', t.audioPlayTime) <> 0)
  """)
  Page<TrecordEntity> findByAudioBasenames(@Param("basenames") java.util.Collection<String> basenames,
      Pageable pageable);

  @Query("""
    SELECT t FROM TrecordEntity t
     WHERE LOWER(function('substring_index', function('replace', t.audioFileDir, '\\\\', '/'), '/', -1)) IN (:basenames)
       AND (t.audioPlayTime IS NULL OR function('time_to_sec', t.audioPlayTime) <> 0)
       AND (:direction = 'ALL' OR (:direction = 'IN' AND t.ioDiscdVal = '수신') OR (:direction = 'OUT' AND t.ioDiscdVal = '발신'))
       AND (:start IS NULL OR t.callStartDateTime >= :start)
       AND (:end IS NULL OR t.callStartDateTime <= :end)
       AND (
            :number IS NULL
         OR (:numberKind = 'EXT'   AND (t.number1 = :ext OR t.number2 = :ext))
         OR (:numberKind = 'PHONE' AND (t.number1 LIKE CONCAT('%', :phoneEnd) OR t.number2 LIKE CONCAT('%', :phoneEnd)))
         OR (:numberKind = 'ALL'   AND ((t.number1 = :ext OR t.number2 = :ext) OR (t.number1 LIKE CONCAT('%', :phoneEnd) OR t.number2 LIKE CONCAT('%', :phoneEnd))))
       )
  """)
  Page<TrecordEntity> findByBasenamesAndFilters(@Param("basenames") java.util.Collection<String> basenames,
      @Param("direction") String direction,
      @Param("numberKind") String numberKind,
      @Param("number") String number,
      @Param("ext") String ext,
      @Param("phoneEnd") String phoneEnd,
      @Param("start") java.time.LocalDateTime start,
      @Param("end")   java.time.LocalDateTime end,
      Pageable pageable);

  @Query("""
    SELECT COUNT(t) FROM TrecordEntity t
     WHERE (:direction = 'ALL' OR (:direction = 'IN' AND t.ioDiscdVal = '수신') OR (:direction = 'OUT' AND t.ioDiscdVal = '발신'))
       AND (:start IS NULL OR t.callStartDateTime >= :start)
       AND (:end   IS NULL OR t.callStartDateTime <= :end)
       AND (
            :number IS NULL
         OR (:numberKind = 'EXT'   AND (t.number1 = :ext OR t.number2 = :ext))
         OR (:numberKind = 'PHONE' AND (t.number1 LIKE CONCAT('%', :phoneEnd) OR t.number2 LIKE CONCAT('%', :phoneEnd)))
         OR (:numberKind = 'ALL'   AND ((t.number1 = :ext OR t.number2 = :ext) OR (t.number1 LIKE CONCAT('%', :phoneEnd) OR t.number2 LIKE CONCAT('%', :phoneEnd))))
       )
  """)
  long countByFilters(@Param("direction") String direction,
      @Param("numberKind") String numberKind,
      @Param("number") String number,
      @Param("ext") String ext,
      @Param("phoneEnd") String phoneEnd,
      @Param("start") java.time.LocalDateTime start,
      @Param("end")   java.time.LocalDateTime end);

  @Query("SELECT t FROM TrecordEntity t WHERE LOWER(t.audioFileDir) LIKE CONCAT('%', LOWER(:suffix))")
  List<TrecordEntity> findByAudioFileSuffix(@Param("suffix") String suffix);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE TrecordEntity t SET t.audioFileDir = :path WHERE t.recordSeq = :id")
  int updateAudioPath(@Param("id") Integer id, @Param("path") String path);

  // 내선(지점 제한) + 허용된 번호 집합 검색용
  @Query("""
  SELECT t FROM TrecordEntity t
   WHERE (t.branchSeq = :branchSeq AND (t.number1 IN :numbers OR t.number2 IN :numbers))
      OR (t.number1 IN :numbers OR t.number2 IN :numbers)
""")
  Page<TrecordEntity> findByBranchAndExtensionsOrNumberOnly(
      @Param("branchSeq") Integer branchSeq,
      @Param("numbers")   List<String> numbers,
      Pageable pageable
  );


  Optional<TrecordEntity> findFirstByAudioFileDir(String audioFileDir);

  @Query("""
  SELECT t FROM TrecordEntity t
   WHERE (t.audioPlayTime IS NULL OR function('time_to_sec', t.audioPlayTime) = 0)
      OR (t.callEndDateTime IS NULL AND t.callStartDateTime IS NOT NULL)
""")
  List<TrecordEntity> findWithoutDuration();
}
