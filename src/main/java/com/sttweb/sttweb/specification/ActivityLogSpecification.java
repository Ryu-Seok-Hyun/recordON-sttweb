package com.sttweb.sttweb.specification;

import com.sttweb.sttweb.entity.TactivitylogEntity;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Locale;

public final class ActivityLogSpecification {

  private ActivityLogSpecification(){}

  /**
   * ANY 문자열 필드에 대해 대소문자 무시 부분일치.
   * CLOB/TEXT도 안전하게 처리하기 위해 CONCAT('', COALESCE(col, ''))로 문자열 캐스팅 유도 후 lower() 적용.
   */
  public static Specification<TactivitylogEntity> containsField(String field, String value){
    return (root, q, cb) -> {
      if (!StringUtils.hasText(value)) return null;

      // CLOB(TEXT) → VARCHAR 캐스팅 유도 + null 안전
      Expression<String> casted = cb.concat("", cb.coalesce(root.get(field), ""));
      return cb.like(cb.lower(casted), "%" + value.toLowerCase(Locale.ROOT) + "%");
    };
  }

  /** userId 정확히 일치 */
  public static Specification<TactivitylogEntity> hasUserId(String uid){
    return (root, q, cb) -> StringUtils.hasText(uid) ? cb.equal(root.get("userId"), uid) : null;
  }

  /** branchSeq 일치 */
  public static Specification<TactivitylogEntity> eqBranch(Integer seq){
    return (root, q, cb) -> seq == null ? null : cb.equal(root.get("branchSeq"), seq);
  }

  /** 문자열 crtime BETWEEN (yyyy-MM-dd HH:mm:ss) */
  public static Specification<TactivitylogEntity> betweenCrtime(String s, String e){
    return (root, q, cb) -> (s != null && e != null) ? cb.between(root.get("crtime"), s, e) : null;
  }

  /** pbIp 또는 pvIp LIKE (IP는 보통 대소문자 이슈가 없지만 일관성 위해 캐스팅만 적용) */
  public static Specification<TactivitylogEntity> ipLike(String ip){
    return (root, q, cb) -> {
      if (!StringUtils.hasText(ip)) return null;
      Expression<String> pb = cb.concat("", cb.coalesce(root.get("pbIp"), ""));
      Expression<String> pv = cb.concat("", cb.coalesce(root.get("pvIp"), ""));
      String like = "%" + ip + "%";
      return cb.or(cb.like(pb, like), cb.like(pv, like));
    };
  }

  /** 특정 userId 제외 (예: IQ200admin 제외) */
  public static Specification<TactivitylogEntity> notUserId(String userId) {
    return (root, query, cb) -> cb.notEqual(root.get("userId"), userId);
  }
}
