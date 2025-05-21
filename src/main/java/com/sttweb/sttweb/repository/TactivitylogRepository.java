package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TactivitylogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TactivitylogRepository
    extends JpaRepository<TactivitylogEntity, Integer>,
    JpaSpecificationExecutor<TactivitylogEntity> {
  // JpaSpecificationExecutor 추가로 findAll(Specification, Pageable) 사용 가능
}
