// src/main/java/com/sttweb/sttweb/repository/TmemberRoleRepository.java
package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.TmemberRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * tmember_role 테이블 전용 JPA Repository
 */
public interface TmemberRoleRepository extends JpaRepository<TmemberRoleEntity, Integer> {
  // 필요 시 findByRoleCode(String roleCode) 등을 추가 가능
}
