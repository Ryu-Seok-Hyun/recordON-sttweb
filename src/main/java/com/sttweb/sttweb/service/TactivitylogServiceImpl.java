// src/main/java/com/sttweb/sttweb/service/impl/TactivitylogServiceImpl.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TactivitylogDto;
import com.sttweb.sttweb.entity.TactivitylogEntity;
import com.sttweb.sttweb.exception.ResourceNotFoundException;
import com.sttweb.sttweb.repository.TactivitylogRepository;
import com.sttweb.sttweb.service.TactivitylogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        .orElseThrow(() ->
            new ResourceNotFoundException("ActivityLog not found: " + activitySeq));
    TactivitylogDto dto = new TactivitylogDto();
    BeanUtils.copyProperties(entity, dto);
    return dto;
  }

  @Override
  public Page<TactivitylogDto> getLogs(Pageable pageable) {
    return repository.findAll(pageable)
        .map(entity -> {
          TactivitylogDto dto = new TactivitylogDto();
          BeanUtils.copyProperties(entity, dto);
          return dto;
        });
  }

  @Override
  public void deleteLog(Integer activitySeq) {
    if (!repository.existsById(activitySeq)) {
      throw new ResourceNotFoundException("ActivityLog not found: " + activitySeq);
    }
    repository.deleteById(activitySeq);
  }
}
