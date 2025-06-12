package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TlineDto;
import com.sttweb.sttweb.entity.Tline;
import com.sttweb.sttweb.repository.TlineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TlineService {

  private final TlineRepository lineRepository;

  /**
   * 특정 라인 ID로 라인 정보 조회
   */
  public TlineDto.Info getLineById(Integer lineId) {
    return lineRepository.findById(lineId)
        .map(this::convertToDto)
        .orElse(null);
  }

  /**
   * 모든 라인 조회
   */
  public List<TlineDto.Info> getAllLines() {
    return lineRepository.findAll().stream()
        .map(this::convertToDto)
        .collect(Collectors.toList());
  }

  /**
   * 라인 번호로 라인 정보 조회
   */
  public TlineDto.Info getLineByCallNum(String callNum) {
    return lineRepository.findByCallNum(callNum)
        .map(this::convertToDto)
        .orElse(null);
  }

  /**
   * 특정 사용자가 접근 가능한 라인 번호들 조회
   */
  public List<String> getAccessibleCallNumbers(String userId) {
    // UserPermissionRepository를 통해 사용자의 라인 권한 조회
    // 이 메서드는 TrecordController에서 사용될 예정
    return Collections.emptyList(); // 구현 필요
  }

  /**
   * Entity → DTO 변환
   */
  private TlineDto.Info convertToDto(Tline entity) {
    return TlineDto.Info.builder()
        .lineId(entity.getLineId())
        .callNum(entity.getCallNum())
        .lineName(entity.getLineName())
        .build();
  }
}