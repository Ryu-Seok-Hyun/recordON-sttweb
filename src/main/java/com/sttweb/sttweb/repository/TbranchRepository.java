package com.sttweb.sttweb.repository;


import com.sttweb.sttweb.entity.TbranchEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TbranchRepository extends JpaRepository<TbranchEntity, Integer> {

  Page<TbranchEntity> findByCompanyNameContainingIgnoreCase(String name, Pageable pageable);

  Optional<TbranchEntity> findBypIp(String pIp); //  맞음 (소문자 p)

}
