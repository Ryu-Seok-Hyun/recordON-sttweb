package com.sttweb.sttweb.repository;


import com.sttweb.sttweb.entity.TbranchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TbranchRepository extends JpaRepository<TbranchEntity, Integer> {

  Page<TbranchEntity> findByCompanyNameContainingIgnoreCase(String name, Pageable pageable);
}
