package com.sttweb.sttweb.repository;


import com.sttweb.sttweb.entity.TbranchEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TbranchRepository extends JpaRepository<TbranchEntity, Integer> {

  Page<TbranchEntity> findByCompanyNameContainingIgnoreCase(String name, Pageable pageable);

  Optional<TbranchEntity> findBypIp(String pIp); //  맞음 (소문자 p)

  Optional<TbranchEntity> findByPbIp(String pbIp);

  /** hqYn 컬럼이 0(본사)인 지점 중 첫 번째 엔티티를 Optional 로 가져온다 */
  Optional<TbranchEntity> findTopByHqYn(String hqYn);

  boolean existsBypIp(String pIp);

  // TbranchRepository.java
  @Query("""
     SELECT b FROM TbranchEntity b
     WHERE (b.pIp  = :ip AND CAST(b.pPort  AS string) = :port)
        OR (b.pbIp = :ip AND CAST(b.pbPort AS string) = :port)
     """)
  List<TbranchEntity> findByIpAndPort(
      @Param("ip") String ip,
      @Param("port") String port
  );

  Page<TbranchEntity> findByIsAlive(boolean isAlive, Pageable pageable);
  Page<TbranchEntity> findByCompanyNameContainingAndIsAlive(String keyword, boolean isAlive, Pageable pageable);

  /** 회사 ID로 지점 개수 조회 */
  @Query("SELECT COUNT(b) FROM TbranchEntity b WHERE b.companyId = :companyId")
  int countByCompanyId(@Param("companyId") Long companyId);
  /**
   * hqYn 컬럼이 '1'인(지사) 레코드가 하나라도 있으면 true
   */
  boolean existsByHqYn(String hqYn);
}
