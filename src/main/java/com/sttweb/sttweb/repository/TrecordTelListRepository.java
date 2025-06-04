// src/main/java/com/sttweb/sttweb/repository/TrecordTelListRepository.java
package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TrecordTelListEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * trecord_tel_list 테이블 전용 JPA Repository
 */
@Repository
public interface TrecordTelListRepository extends JpaRepository<TrecordTelListEntity, Integer> {
  /**
   * callNum(내선번호)로 단일 레코드 조회 (중복 검사 용도)
   */
  Optional<TrecordTelListEntity> findByCallNum(String callNum);
}
