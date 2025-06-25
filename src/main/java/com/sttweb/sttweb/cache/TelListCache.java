package com.sttweb.sttweb.cache;

import com.sttweb.sttweb.entity.TrecordTelListEntity;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TelListCache {
  private final TrecordTelListRepository repo;
  private volatile Map<Integer, String> cache = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    reload();
  }

  /** 전체 내선 리스트를 다시 로딩 */
  public synchronized void reload() {
    cache = repo.findAll()
        .stream()
        .collect(Collectors.toMap(
            TrecordTelListEntity::getId,
            TrecordTelListEntity::getCallNum
        ));
  }

  /** ID로 전화번호 조회 (없으면 null) */
  public String getCallNum(Integer lineId) {
    return cache.get(lineId);
  }
}
