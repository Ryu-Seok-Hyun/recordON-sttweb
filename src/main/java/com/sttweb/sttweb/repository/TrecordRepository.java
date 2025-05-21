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

  @Query("""
    select t
      from TrecordEntity t
     where (:num1    is null or t.number1    = :num1)
       and (:num2    is null or t.number2    = :num2)
       and (:inbound is null 
            or t.ioDiscdVal = case when :inbound = true then '수신' else '발신' end)
       and (
            :isExt is null
         or ( :isExt = true  and (
                (t.ioDiscdVal = '수신' and length(t.number2) <= 4)
             or (t.ioDiscdVal = '발신' and length(t.number1) <= 4)
             ))
         or ( :isExt = false and (
                (t.ioDiscdVal = '수신' and length(t.number1) > 4)
             or (t.ioDiscdVal = '발신' and length(t.number2) > 4)
             ))
       )
       and (:start is null or t.callStartDateTime >= :start)
       and (:end   is null or t.callEndDateTime   <= :end)
       and (
            :q is null
         or t.callStatus like concat('%', :q, '%')
         or t.number1    like concat('%', :q, '%')
         or t.number2    like concat('%', :q, '%')
       )
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
