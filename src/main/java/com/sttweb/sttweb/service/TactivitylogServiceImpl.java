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

    // 1) 일반 사용자는 자신의 로그만
    if (!"0".equals(userLevel)) {
      spec = spec.and(ActivityLogSpecification.hasUserId(userId));
    }

    // 2) 구분(type) 필터
    // 2) 구분(type) 필터: 한국어 레이블 → activity 컬럼(한글) 매핑
    if (type != null && !type.isBlank() && !"전체".equals(type)) {
      switch (type) {
        case "조회":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "조회"));
          break;
        case "청취":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "청취"));
          break;
        case "다운로드":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "다운로드"));
          break;
        case "수정":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "수정"));
          break;
        case "등록":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "등록"));
          break;
        case "비활성화":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "비활성화"));
          break;
        case "활성화":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "활성화"));
          break;
        case "로그인":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "로그인"));
          break;
        case "로그아웃":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "로그아웃"));
          break;
        case "부여":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "부여"));
          break;
        case "회수":
          spec = spec.and(ActivityLogSpecification.containsField("activity", "회수"));
          break;
        default:
          // 기타: activity 컬럼에 키워드 포함 검색
          spec = spec.and(ActivityLogSpecification.containsField("activity", type));
      }
    }

    // 3) 날짜범위 필터
    if (startCrtime != null && endCrtime != null) {
      spec = spec.and(ActivityLogSpecification.betweenCrtime(startCrtime, endCrtime));
    }

    // 4) 검색필드 + 키워드 필터
    if (searchField != null && !searchField.isBlank()
        && keyword    != null && !keyword.isBlank()) {
      String q       = keyword.trim();
      switch (searchField) {
        case "userId":
          spec = spec.and(ActivityLogSpecification.containsField("userId", q));
          break;
        case "ip":
          spec = spec.and(ActivityLogSpecification.ipLike(q));
          break;
        case "activity":
          spec = spec.and(ActivityLogSpecification.containsField("activity", q));
          break;
        case "contents":
          spec = spec.and(ActivityLogSpecification.containsField("contents", q));
          break;
        case "activityContent":
          // activity OR contents
          spec = spec.and(
              ActivityLogSpecification.containsField("activity", q)
                  .or(ActivityLogSpecification.containsField("contents", q))
          );
          break;
        default:
          // ALL 혹은 미지정 시 필터 없음
          break;
      }
    }

    return repository.findAll(spec, pageable)
        .map(this::toDto);
  }

  /** Entity → DTO 변환 헬퍼 */
  private TactivitylogDto toDto(TactivitylogEntity entity) {
    TactivitylogDto dto = new TactivitylogDto();
    BeanUtils.copyProperties(entity, dto);
    return dto;
  }
}
