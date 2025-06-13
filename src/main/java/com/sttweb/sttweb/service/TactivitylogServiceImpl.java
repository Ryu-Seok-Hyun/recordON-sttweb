package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.entity.TactivitylogEntity;
import com.sttweb.sttweb.exception.ResourceNotFoundException;
import com.sttweb.sttweb.repository.TactivitylogRepository;
import com.sttweb.sttweb.service.TactivitylogService;
import com.sttweb.sttweb.specification.ActivityLogSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TactivitylogServiceImpl implements TactivitylogService {

  private final TactivitylogRepository repository;

  @Override
  public TactivitylogDto createLog(TactivitylogDto dto) {
    TactivitylogEntity entity = new TactivitylogEntity();
    BeanUtils.copyProperties(dto, entity);
    TactivitylogEntity saved = repository.save(entity);
    TactivitylogDto result = new TactivitylogDto();
    BeanUtils.copyProperties(saved, result);
    return result;
  }

  @Override
  public TactivitylogDto getLog(Integer activitySeq) {
    TactivitylogEntity entity = repository.findById(activitySeq)
        .orElseThrow(() -> new ResourceNotFoundException("ActivityLog not found: " + activitySeq));
    return toDto(entity);
  }

  @Override
  public void deleteLog(Integer activitySeq) {
    if (!repository.existsById(activitySeq)) {
      throw new ResourceNotFoundException("ActivityLog not found: " + activitySeq);
    }
    repository.deleteById(activitySeq);
  }

  @Override
  public Page<TactivitylogDto> getLogs(Pageable pageable) {
    return repository.findAll(pageable)
        .map(entity -> toDto(entity));
  }

  @Override
  public Page<TactivitylogDto> getLogsByUserId(String userId, Pageable pageable) {
    return repository.findAll(
        ActivityLogSpecification.hasUserId(userId),
        pageable
    ).map(entity -> toDto(entity));
  }




  @Override
  public Page<TactivitylogDto> getLogsWithFilter(
      String userId,
      String userLevel,
      String startCrtime,
      String endCrtime,
      String type,
      String searchField,
      String keyword,
      Pageable pageable
  ) {
    Specification<TactivitylogEntity> spec = Specification.where(null);

    if (!"0".equals(userLevel)) {
      spec = spec.and(ActivityLogSpecification.hasUserId(userId));
    }

    if (type != null && !type.isBlank() && !"전체".equals(type)) {
      switch (type) {
        case "조회":       spec = spec.and(ActivityLogSpecification.containsField("activity", "조회")); break;
        case "청취":       spec = spec.and(ActivityLogSpecification.containsField("activity", "청취")); break;
        case "다운로드":   spec = spec.and(ActivityLogSpecification.containsField("activity", "다운로드")); break;
        case "수정":       spec = spec.and(ActivityLogSpecification.containsField("activity", "수정")); break;
        case "등록":       spec = spec.and(ActivityLogSpecification.containsField("activity", "등록")); break;
        case "비활성화":   spec = spec.and(ActivityLogSpecification.containsField("activity", "비활성화")); break;
        case "활성화":     spec = spec.and(ActivityLogSpecification.containsField("activity", "활성화")); break;
        case "로그인":     spec = spec.and(ActivityLogSpecification.containsField("activity", "로그인")); break;
        case "로그아웃":   spec = spec.and(ActivityLogSpecification.containsField("activity", "로그아웃")); break;
        case "부여":       spec = spec.and(ActivityLogSpecification.containsField("activity", "부여")); break;
        case "회수":       spec = spec.and(ActivityLogSpecification.containsField("activity", "회수")); break;
        default:           spec = spec.and(ActivityLogSpecification.containsField("activity", type));
      }
    }

    if (startCrtime != null && endCrtime != null) {
      spec = spec.and(ActivityLogSpecification.betweenCrtime(startCrtime, endCrtime));
    }

    // *** 여기만 바꿔주면 됨! ***
    if (keyword != null && !keyword.isBlank()) {
      String q = keyword.trim();
      if (searchField == null || searchField.isBlank() || "전체".equals(searchField)) {
        // 전체검색: 모든 주요 필드 OR
        spec = spec.and(
            ActivityLogSpecification.containsField("userId", q)
                .or(ActivityLogSpecification.containsField("activity", q))
                .or(ActivityLogSpecification.containsField("contents", q))
                .or(ActivityLogSpecification.containsField("companyName", q))
                .or(ActivityLogSpecification.containsField("pbIp", q))
                .or(ActivityLogSpecification.containsField("pvIp", q))
        );
      } else {
        switch (searchField) {
          case "userId":
            spec = spec.and(ActivityLogSpecification.containsField("userId", q)); break;
          case "ip":
            spec = spec.and(ActivityLogSpecification.ipLike(q)); break;
          case "pbIp":
            spec = spec.and(ActivityLogSpecification.containsField("pbIp", q)); break;
          case "pvIp":
            spec = spec.and(ActivityLogSpecification.containsField("pvIp", q)); break;
          case "activity":
            spec = spec.and(ActivityLogSpecification.containsField("activity", q)); break;
          case "contents":
            spec = spec.and(ActivityLogSpecification.containsField("contents", q)); break;
          case "activityContent":
            spec = spec.and(
                ActivityLogSpecification.containsField("activity", q)
                    .or(ActivityLogSpecification.containsField("contents", q))
            ); break;
          case "branch":
          case "지점":
            spec = spec.and(ActivityLogSpecification.containsField("companyName", q)); break;
          default:
            spec = spec.and(
                ActivityLogSpecification.containsField("userId", q)
                    .or(ActivityLogSpecification.containsField("activity", q))
                    .or(ActivityLogSpecification.containsField("contents", q))
                    .or(ActivityLogSpecification.containsField("companyName", q))
                    .or(ActivityLogSpecification.containsField("pbIp", q))
                    .or(ActivityLogSpecification.containsField("pvIp", q))
            ); break;
        }
      }
    }

    return repository.findAll(spec, pageable).map(this::toDto);
  }


  /**
     * Entity → DTO 변환 헬퍼
     */
  private TactivitylogDto toDto(TactivitylogEntity entity) {
    TactivitylogDto dto = new TactivitylogDto();
    BeanUtils.copyProperties(entity, dto);
    return dto;
  }
}