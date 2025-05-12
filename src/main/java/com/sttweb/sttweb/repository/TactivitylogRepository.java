// src/main/java/com/sttweb/sttweb/repository/TactivitylogRepository.java
package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TactivitylogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TactivitylogRepository extends JpaRepository<TactivitylogEntity, Integer> {
}
