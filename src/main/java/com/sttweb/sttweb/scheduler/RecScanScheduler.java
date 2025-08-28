package com.sttweb.sttweb.scheduler;

import com.sttweb.sttweb.service.TrecordScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecScanScheduler {

  private final TrecordScanService scanSvc;

  /** 5분마다 RecOnData 폴더 스캔 + 길이/종료시각 백필 */
  @Scheduled(fixedDelayString = "PT5M")
  @SchedulerLock(
      name = "recScanTask",
      lockAtLeastFor = "PT4M",
      lockAtMostFor  = "PT14M"
  )
  public void scheduledScan() {
    log.info("==> RecOnData 자동 스캔 시작");
    try {
      int inserted   = scanSvc.scanAndSaveNewRecords();
      int backfilled = scanSvc.backfillMissingDurations();
      log.info("==> RecOnData 자동 스캔 완료 (신규: {}, 길이/종료 보정: {})", inserted, backfilled);
    } catch (Exception e) {
      log.error("RecOnData 스캔 중 오류 발생", e);
    }
  }
}
