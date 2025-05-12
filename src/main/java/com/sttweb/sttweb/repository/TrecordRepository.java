package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TrecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrecordRepository extends JpaRepository<TrecordEntity, Integer> {
  // 기존 검색 메서드
  List<TrecordEntity> findByNumber1(String number1);
  List<TrecordEntity> findByNumber2(String number2);
  List<TrecordEntity> findByNumber1OrNumber2(String number1, String number2);

  // 페이징 지원 검색 메서드
  Page<TrecordEntity> findByNumber1(String number1, Pageable pageable);
  Page<TrecordEntity> findByNumber2(String number2, Pageable pageable);
  Page<TrecordEntity> findByNumber1OrNumber2(String number1, String number2, Pageable pageable);
}
