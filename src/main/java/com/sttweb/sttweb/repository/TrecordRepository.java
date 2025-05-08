// src/main/java/com/sttweb/sttweb/repository/TrecordRepository.java
package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TrecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrecordRepository extends JpaRepository<TrecordEntity, Integer> {
  List<TrecordEntity> findByNumber1(String number1);
  List<TrecordEntity> findByNumber2(String number2);
  List<TrecordEntity> findByNumber1OrNumber2(String number1, String number2);
}
