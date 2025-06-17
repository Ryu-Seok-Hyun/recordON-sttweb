package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TbranchDto;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.repository.TbranchRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TbranchServiceImpl implements TbranchService {

  private final TbranchRepository repo;
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private TbranchDto toDto(TbranchEntity e) {
    return TbranchDto.builder()
        .branchSeq(e.getBranchSeq())
        .companyId(e.getCompanyId())
        .companyName(e.getCompanyName())
        .phone(e.getPhone())
        .ipType(e.getIpType())
        .pIp(e.getPIp())
        .pPort(e.getPPort())
        .pbIp(e.getPbIp())
        .pbPort(e.getPbPort())
        .hqYn(e.getHqYn())
        .discd(e.getDiscd())
        .crtime(e.getCrtime().format(FMT))

        // ================================
        // ※ 아래 세 줄을 반드시 추가해야 합니다.
        .isAlive(e.getIsAlive())
        .lastHealthCheck(e.getLastHealthCheck())
        .lastDowntime(e.getLastDowntime())
        // ================================

        .build();
  }

  /**
   * 페이징 처리된 전체 조회
   */
  @Override
  @Transactional(readOnly = true)
  public Page<TbranchDto> findAll(Pageable pageable) {
    return repo.findAll(pageable)
        .map(this::toDto);
  }

  /**
   * 회사명으로 검색
   */
  @Override
  @Transactional(readOnly = true)
  public Page<TbranchDto> search(String keyword, Pageable pageable) {
    return repo.findByCompanyNameContainingIgnoreCase(keyword, pageable)
        .map(this::toDto);
  }

  /**
   * 내부망 IP(p_ip) 로 엔티티 조회
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<TbranchEntity> findBypIp(String pIp) {
    return repo.findBypIp(pIp);
  }

  /**
   * public IP(pb_ip) 로 엔티티 조회
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<TbranchEntity> findByPbIp(String pbIp) {
    return repo.findByPbIp(pbIp);
  }

  /**
   * public IP 로 DTO 반환
   */
  @Override
  @Transactional(readOnly = true)
  public TbranchDto findByPublicIp(String ip) {
    TbranchEntity e = repo.findByPbIp(ip)
        .orElseThrow(() -> new IllegalArgumentException("해당 public IP에 해당하는 지사가 없습니다: " + ip));
    return toDto(e);
  }

  /**
   * branchSeq 로 단건 조회
   */
  @Override
  @Transactional(readOnly = true)
  public TbranchDto findById(Integer branchSeq) {
    TbranchEntity e = repo.findById(branchSeq)
        .orElseThrow(() -> new IllegalArgumentException("지점을 찾을 수 없습니다: " + branchSeq));
    return toDto(e);
  }

  /**
   * 신규 지점 생성
   */
  @Override
  @Transactional
  public TbranchDto createBranch(TbranchDto dto) {
    // p_ip 중복 검사
    if (dto.getPIp() != null && repo.existsBypIp(dto.getPIp())) {
      throw new IllegalArgumentException("이미 사용 중인 내부망 IP(p_ip)입니다: " + dto.getPIp());
    }

    TbranchEntity e = new TbranchEntity();
    e.setCompanyId(dto.getCompanyId());
    e.setPhone(dto.getPhone());
    e.setCompanyName(dto.getCompanyName());
    e.setIpType(dto.getIpType());
    e.setPIp(dto.getPIp());
    e.setPPort(dto.getPPort());
    e.setPbIp(dto.getPbIp());
    e.setPbPort(dto.getPbPort());
    e.setHqYn(dto.getHqYn());
    e.setDiscd(0);
    return toDto(repo.save(e));
  }

  /**
   * hqYn = "0"인 본사 지점을 하나 가져온다.
   */
  @Override
  public Optional<TbranchEntity> findHqBranch() {
    return repo.findTopByHqYn("0");
  }

  /**
   * 지점 정보 업데이트
   */
  @Override
  @Transactional
  public TbranchDto update(Integer branchSeq, TbranchDto dto) {
    TbranchEntity e = repo.findById(branchSeq)
        .orElseThrow(() -> new IllegalArgumentException("지점을 찾을 수 없습니다: " + branchSeq));
    e.setCompanyId(dto.getCompanyId());
    e.setPhone(dto.getPhone());
    e.setCompanyName(dto.getCompanyName());
    e.setIpType(dto.getIpType());
    e.setPIp(dto.getPIp());
    e.setPPort(dto.getPPort());
    e.setPbIp(dto.getPbIp());
    e.setPbPort(dto.getPbPort());
    e.setHqYn(dto.getHqYn());
    return toDto(repo.save(e));
  }

  /**
   * 활성/비활성 변경
   */
  @Override
  @Transactional
  public void changeStatus(Integer branchSeq, boolean active) {
    TbranchEntity e = repo.findById(branchSeq)
        .orElseThrow(() -> new IllegalStateException("지점을 찾을 수 없습니다: " + branchSeq));
    e.setDiscd(active ? 0 : 1);
    repo.save(e);
  }

  /**
   * branchSeq → entity 조회
   */
  @Override
  public TbranchEntity findEntityBySeq(Integer branchSeq) {
    return repo.findById(branchSeq)
        .orElseThrow(() -> new IllegalArgumentException("지점 정보를 찾을 수 없습니다: " + branchSeq));
  }

  @Override
  @Transactional(readOnly = true)
  public List<TbranchEntity> findAllEntities() {
    return repo.findAll();
  }

  @Override
  public Optional<TbranchEntity> findByIpAndPort(String ip, String port) {
    return repo.findByIpAndPort(ip, port);
  }
}
