package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TrecordEntity;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;  // 추가
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrecordRepository
    extends JpaRepository<TrecordEntity, Integer>,
    JpaSpecificationExecutor<TrecordEntity> {  // 여기에 추가

  Page<TrecordEntity> findByNumber1(String number1, Pageable pageable);
  Page<TrecordEntity> findByNumber2(String number2, Pageable pageable);
  Page<TrecordEntity> findByNumber1OrNumber2(String number1, String number2, Pageable pageable);

  /**
   * @param num1    첫 번째 번호 필터 (null 이면 무시)
   * @param num2    두 번째 번호 필터 (null 이면 무시)
   * @param inbound IN/OUT 구분 (null=ALL)
   * @param isExt   내선(EXT)=true, 전화번호(PHONE)=false, ALL=null
   * @param q       검색어 (null 이면 무시)
   * @param start   시작시간 (null 이면 무시)
   * @param end     종료시간 (null 이면 무시)
   */
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
           and (t.callStatus like concat('%',:q,'%')
             or t.number1    like concat('%',:q,'%')
             or t.number2    like concat('%',:q,'%'))
          )
         or (
              :isExt = true
           and (t.callStatus like concat('%',:q,'%')
             or t.number1    like concat('%',:q,'%'))
          )
         or (
              :isExt = false
           and (t.callStatus like concat('%',:q,'%')
             or t.number2    like concat('%',:q,'%'))
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

}