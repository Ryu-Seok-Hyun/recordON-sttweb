// src/main/java/com/sttweb/sttweb/service/TbranchServiceImpl.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.repository.TbranchRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TbranchServiceImpl implements TbranchService {
  private final TbranchRepository repo;
  private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*\\d)(?=.*\\W).{8,}$";

  private TbranchDto toDto(TbranchEntity e) {
    return TbranchDto.builder()
        .branchSeq(e.getBranchSeq())
        .companyId(e.getCompanyId())
        .companyName(e.getCompanyName())
        .phone(e.getPhone())
        .ipType(e.getIpType())
        .pbIp(e.getPbIp())
        .pbPort(e.getPbPort())
        .pIp(e.getPIp())
        .pPort(e.getPPort())
        .hqYn(e.getHqYn())
        .discd(e.getDiscd())
        .build();
  }

  /** 페이징 처리된 전체 조회 구현 */
  @Override
  @Transactional(readOnly = true)
  public Page<TbranchDto> findAll(Pageable pageable) {
    return repo.findAll(pageable)
        .map(this::toDto);
  }
  @Override
  @Transactional(readOnly = true)
  public Page<TbranchDto> search(String keyword, Pageable pageable) {
    return repo
        .findByCompanyNameContainingIgnoreCase(keyword, pageable)
        .map(this::toDto);
  }

  @Override
  public TbranchDto findByPublicIp(String ip) {
    return repo.findBypIp(ip)
        .map(TbranchDto::fromEntity)
        .orElseThrow(() -> new IllegalArgumentException("해당 IP에 해당하는 지사가 없습니다: " + ip));
  }

  @Override
  public Optional<TbranchEntity> findBypIp(String pIp) {
    return repo.findBypIp(pIp);
  }

  @Override
  @Transactional(readOnly = true)
  public TbranchDto findById(Integer branchSeq) {
    TbranchEntity e = repo.findById(branchSeq)
        .orElseThrow(() -> new IllegalArgumentException("지점을 찾을 수 없습니다: " + branchSeq));
    return toDto(e);
  }

  @Override
  @Transactional
  public TbranchDto createBranch(TbranchDto dto) {
    TbranchEntity e = new TbranchEntity();
    e.setCompanyId(dto.getCompanyId());
    e.setPhone(dto.getPhone());
    e.setCompanyName(dto.getCompanyName());
    e.setIpType(dto.getIpType());
    e.setPbIp(dto.getPbIp());
    e.setPbPort(dto.getPbPort());
    e.setPIp(dto.getPIp());
    e.setPPort(dto.getPPort());
    e.setHqYn(dto.getHqYn());
    e.setDiscd(0);
    return toDto(repo.save(e));
  }

  @Override
  @Transactional
  public TbranchDto update(Integer branchSeq, TbranchDto dto) {
    TbranchEntity e = repo.findById(branchSeq)
        .orElseThrow(() -> new IllegalArgumentException("지점을 찾을 수 없습니다: " + branchSeq));
    e.setCompanyId(dto.getCompanyId());
    e.setPhone(dto.getPhone());
    e.setCompanyName(dto.getCompanyName());
    e.setIpType(dto.getIpType());
    e.setPbIp(dto.getPbIp());
    e.setPbPort(dto.getPbPort());
    e.setPIp(dto.getPIp());
    e.setPPort(dto.getPPort());
    e.setHqYn(dto.getHqYn());
    return toDto(repo.save(e));
  }

  @Override
  @Transactional
  public void changeStatus(Integer branchSeq, boolean active) {
    TbranchEntity e = repo.findById(branchSeq)
        .orElseThrow(() -> new IllegalStateException("지점을 찾을 수 없습니다."));
    e.setDiscd(active ? 0 : 1);
    repo.save(e);
  }

}
