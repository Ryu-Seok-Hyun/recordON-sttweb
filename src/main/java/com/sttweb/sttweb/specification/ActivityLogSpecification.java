package com.sttweb.sttweb.specification;

import com.sttweb.sttweb.entity.TactivitylogEntity;
import org.springframework.data.jpa.domain.Specification;

public class ActivityLogSpecification {

  /** 사용자 아이디 필터 */
  public static Specification<TactivitylogEntity> hasUserId(String userId) {
    return (root, query, cb) ->
        cb.equal(root.get("userId"), userId);
  }

  /** type 필터 */
  public static Specification<TactivitylogEntity> hasType(String type) {
    return (root, query, cb) ->
        cb.equal(root.get("type"), type);
  }

  /** crtime 문자열 범위 필터 */
  public static Specification<TactivitylogEntity> betweenCrtime(String start, String end) {
    return (root, query, cb) ->
        cb.between(root.get("crtime"), start, end);
  }

  /** 단일 필드에 LIKE 검색 */
  public static Specification<TactivitylogEntity> containsField(String field, String keyword) {
    return (root, query, cb) ->
        cb.like(root.get(field), "%" + keyword + "%");
  }

  /** 공인(pbIp) 또는 사설(pvIp) IP 중 하나라도 LIKE 검색 */
  public static Specification<TactivitylogEntity> ipLike(String keyword) {
    return (root, query, cb) ->
        cb.or(
            cb.like(root.get("pbIp"), "%" + keyword + "%"),
            cb.like(root.get("pvIp"), "%" + keyword + "%")
        );
  }
}
