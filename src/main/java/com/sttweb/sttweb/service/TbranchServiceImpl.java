package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.repository.TbranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TbranchServiceImpl implements TbranchService {
  private final TbranchRepository repo;

  private TbranchDto toDto(TbranchEntity e) {
    return TbranchDto.builder()
        .branchSeq(e.getBranchSeq())
        .companyId(e.getCompanyId())
        .phone(e.getPhone())
        .companyName(e.getCompanyName())
        .ipType(e.getIpType())
        .pbIp(e.getPbIp())
        .pbPort(e.getPbPort())
        .pIp(e.getPIp())
        .pPort(e.getPPort())
        .hqYn(e.getHqYn())
        .discd(e.getDiscd())
//        .dbType(e.getDbType())
//        .dbIp(e.getDbIp())
//        .dbPort(e.getDbPort())
//        .dbName(e.getDbName())
//        .dbUser(e.getDbUser())
//        .dbPass(e.getDbPass())
//        .dbFlag(e.getDbFlag())
//        .dbDiscd(e.getDbDiscd())
//        .mailDiscd(e.getMailDiscd())
//        .mailManager(e.getMailManager())
//        .mailAddress(e.getMailAddress())
        .build();
  }

  private TbranchEntity toEntity(TbranchDto dto) {
    TbranchEntity e = new TbranchEntity();
    e.setBranchSeq(dto.getBranchSeq());
    e.setCompanyId(dto.getCompanyId());
    e.setPhone(dto.getPhone());
    e.setCompanyName(dto.getCompanyName());
    e.setIpType(dto.getIpType());
    e.setPbIp(dto.getPbIp());
    e.setPbPort(dto.getPbPort());
    e.setPIp(dto.getPIp());
    e.setPPort(dto.getPPort());
    e.setHqYn(dto.getHqYn());
    e.setDiscd(dto.getDiscd());
//    e.setDbType(dto.getDbType());
//    e.setDbIp(dto.getDbIp());
//    e.setDbPort(dto.getDbPort());
//    e.setDbName(dto.getDbName());
//    e.setDbUser(dto.getDbUser());
//    e.setDbPass(dto.getDbPass());
//    e.setDbFlag(dto.getDbFlag());
//    e.setDbDiscd(dto.getDbDiscd());
//    e.setMailDiscd(dto.getMailDiscd());
//    e.setMailManager(dto.getMailManager());
//    e.setMailAddress(dto.getMailAddress());
    return e;
  }

  @Override
  public List<TbranchDto> findAll() {
    return repo.findAll().stream()
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  @Override
  public TbranchDto findById(Integer branchSeq) {
    TbranchEntity e = repo.findById(branchSeq)
        .orElseThrow(() -> new IllegalArgumentException("지점을 찾을 수 없습니다: " + branchSeq));
    return toDto(e);
  }

  @Override
  @Transactional
  public TbranchDto createBranch(TbranchDto dto) {
    TbranchEntity e = toEntity(dto);
    e.setDiscd(0);  // 기본 활성
    TbranchEntity saved = repo.save(e);
    return toDto(saved);
  }

  @Override
  @Transactional
  public TbranchDto update(Integer branchSeq, TbranchDto dto) {
    TbranchEntity e = repo.findById(branchSeq)
        .orElseThrow(() -> new IllegalArgumentException("지점을 찾을 수 없습니다: " + branchSeq));
    // 수정할 필드들만 덮어쓰기
    e.setCompanyId(dto.getCompanyId());
    e.setPhone(dto.getPhone());
    e.setCompanyName(dto.getCompanyName());
    e.setIpType(dto.getIpType());
    e.setPbIp(dto.getPbIp());
    e.setPbPort(dto.getPbPort());
    e.setPIp(dto.getPIp());
    e.setPPort(dto.getPPort());
    e.setHqYn(dto.getHqYn());
//    e.setDbType(dto.getDbType());
//    e.setDbIp(dto.getDbIp());
//    e.setDbPort(dto.getDbPort());
//    e.setDbName(dto.getDbName());
//    e.setDbUser(dto.getDbUser());
//    e.setDbPass(dto.getDbPass());
//    e.setDbFlag(dto.getDbFlag());
//    e.setDbDiscd(dto.getDbDiscd());
//    e.setMailDiscd(dto.getMailDiscd());
//    e.setMailManager(dto.getMailManager());
//    e.setMailAddress(dto.getMailAddress());
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

  @Override
  @Transactional
  public void deleteBranch(Integer branchSeq) {
    changeStatus(branchSeq, false);
  }
}
