package com.sttweb.sttweb.specification;

import com.sttweb.sttweb.entity.TactivitylogEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import jakarta.persistence.criteria.Predicate;

public final class ActivityLogSpecification {

  private ActivityLogSpecification(){}

  /* LIKE %value% (대소문자 무시) */
  public static Specification<TactivitylogEntity> containsField(String field, String value){
    return (root,q,cb) ->
        StringUtils.hasText(value)
            ? cb.like(cb.lower(root.get(field)), "%"+value.toLowerCase()+"%")
            : null;
  }

  /* userId 일치 */
  public static Specification<TactivitylogEntity> hasUserId(String uid){
    return (root,q,cb) ->
        StringUtils.hasText(uid) ? cb.equal(root.get("userId"),uid) : null;
  }

  /* branchSeq 일치 */
  public static Specification<TactivitylogEntity> eqBranch(Integer seq){
    return (root,q,cb) -> seq==null? null : cb.equal(root.get("branchSeq"),seq);
  }

  /* 문자열 crtime BETWEEN (yyyy-MM-dd HH:mm:ss) */
  public static Specification<TactivitylogEntity> betweenCrtime(String s,String e){
    return (root,q,cb) ->
        (s!=null && e!=null) ? cb.between(root.get("crtime"),s,e) : null;
  }

  /* pbIp 또는 pvIp LIKE */
  public static Specification<TactivitylogEntity> ipLike(String ip){
    return (root,q,cb)->{
      if(!StringUtils.hasText(ip)) return null;
      Predicate pb = cb.like(root.get("pbIp"), "%"+ip+"%");
      Predicate pv = cb.like(root.get("pvIp"), "%"+ip+"%");
      return cb.or(pb,pv);
    };
  }
}
