package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.entity.TactivitylogEntity;
import com.sttweb.sttweb.exception.ResourceNotFoundException;
import com.sttweb.sttweb.repository.TactivitylogRepository;
import com.sttweb.sttweb.specification.ActivityLogSpecification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class TactivitylogServiceImpl implements TactivitylogService {

  private static final String HIDDEN_USER = "IQ200admin";
  private final TactivitylogRepository repository;

  /* ---------- CREATE ---------- */
  @Override
  public TactivitylogDto createLog(TactivitylogDto dto) {
    TactivitylogEntity ent = new TactivitylogEntity();
    BeanUtils.copyProperties(dto, ent);
    BeanUtils.copyProperties(repository.save(ent), dto);
    return dto;
  }

  /* ---------- READ (단건) ---------- */
  @Override
  public TactivitylogDto getLog(Integer seq) {
    TactivitylogEntity ent = repository.findById(seq)
        .orElseThrow(() -> new ResourceNotFoundException("ActivityLog not found: " + seq));
    return toDto(ent);
  }

  /* ---------- DELETE ---------- */
  @Override
  public void deleteLog(Integer seq) {
    if (!repository.existsById(seq))
      throw new ResourceNotFoundException("ActivityLog not found: " + seq);
    repository.deleteById(seq);
  }

  /* ---------- READ (전체) ---------- */
  @Override public Page<TactivitylogDto> getLogs(Pageable p){
    return repository.findAll(p).map(this::toDto);
  }
  @Override public Page<TactivitylogDto> getLogsByUserId(String uid, Pageable p){
    return repository.findAll(ActivityLogSpecification.hasUserId(uid), p)
        .map(this::toDto);
  }

  /* ---------- READ (권한·필터 통합) ---------- */
  @Override
  public Page<TactivitylogDto> getLogsWithFilter(
      String  userId,
      String  userLevel,
      Integer branchSeq,
      String  startCr,
      String  endCr,
      String  type,
      String  field,
      String  keyword,
      Pageable pageable
  ){
    Specification<TactivitylogEntity> spec = Specification.where(null);

    // 항상 IQ200admin 로그는 제외
    spec = spec.and(ActivityLogSpecification.notUserId(HIDDEN_USER));

    /* 1) 권한 영역 */
    if ("1".equals(userLevel))
      spec = spec.and(ActivityLogSpecification.eqBranch(branchSeq));
    else if (!"0".equals(userLevel))
      spec = spec.and(ActivityLogSpecification.hasUserId(userId));

    /* 2) 기간 */
    if (startCr!=null && endCr!=null)
      spec = spec.and(ActivityLogSpecification.betweenCrtime(startCr,endCr));

    /* 3) 활동 타입 */
    if (type!=null && !type.isBlank() && !"전체".equals(type))
      spec = spec.and(ActivityLogSpecification.containsField("activity",type));

    /* 4) 키워드 검색 */
    if (keyword!=null && !keyword.isBlank()){
      String q = keyword.trim();
      if (field==null || field.isBlank() || "전체".equals(field)){
        spec = spec.and(
            ActivityLogSpecification.containsField("userId",q)
                .or(ActivityLogSpecification.containsField("activity",q))
                .or(ActivityLogSpecification.containsField("contents",q))
                .or(ActivityLogSpecification.containsField("companyName",q))
                .or(ActivityLogSpecification.containsField("pbIp",q))
                .or(ActivityLogSpecification.containsField("pvIp",q))
        );
      } else switch(field){
        case "userId"   -> spec = spec.and(ActivityLogSpecification.containsField("userId",q));
        case "ip"       -> spec = spec.and(ActivityLogSpecification.ipLike(q));
        case "pbIp"     -> spec = spec.and(ActivityLogSpecification.containsField("pbIp",q));
        case "pvIp"     -> spec = spec.and(ActivityLogSpecification.containsField("pvIp",q));
        case "activity" -> spec = spec.and(ActivityLogSpecification.containsField("activity",q));
        case "contents" -> spec = spec.and(ActivityLogSpecification.containsField("contents",q));
        case "branch","지점" ->
            spec = spec.and(ActivityLogSpecification.containsField("companyName",q));
        default -> spec = spec.and(
            ActivityLogSpecification.containsField("userId",q)
                .or(ActivityLogSpecification.containsField("activity",q))
                .or(ActivityLogSpecification.containsField("contents",q))
                .or(ActivityLogSpecification.containsField("companyName",q))
                .or(ActivityLogSpecification.containsField("pbIp",q))
                .or(ActivityLogSpecification.containsField("pvIp",q))
        );
      }
    }

    return repository.findAll(spec,pageable).map(this::toDto);
  }

  /* ---------- Entity→DTO ---------- */
  private TactivitylogDto toDto(TactivitylogEntity e){
    TactivitylogDto d = new TactivitylogDto();
    BeanUtils.copyProperties(e,d);
    return d;
  }
}
