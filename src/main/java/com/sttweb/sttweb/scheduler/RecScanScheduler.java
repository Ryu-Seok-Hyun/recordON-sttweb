package com.sttweb.sttweb.scheduler;

import com.sttweb.sttweb.service.TrecordScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecScanScheduler {
  private final TrecordScanService scanSvc;

  /**
   * 5분마다 RecOnData 폴더 스캔
   */
  @Scheduled(fixedDelayString = "PT5M")
  @SchedulerLock(
      name = "recScanTask",
      lockAtLeastFor = "PT4M",    // 최소 락 유지 시간
      lockAtMostFor  = "PT14M"    // 최대 락 유지 시간
  )
  @Transactional                // 트랜잭션 경계 보장
  public void scheduledScan() {
    log.info("==> RecOnData 자동 스캔 시작");
    try {
      scanSvc.scanAndSaveNewRecords();
      log.info("==> RecOnData 자동 스캔 완료");
    } catch (Exception e) {
      log.error("RecOnData 스캔 중 오류 발생", e);
    }
  }
}
