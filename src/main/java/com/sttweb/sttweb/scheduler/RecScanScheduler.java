package com.sttweb.sttweb.scheduler;

import com.sttweb.sttweb.service.TrecordScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecScanScheduler {
  private final TrecordScanService scanSvc;

  /**
   * 5분마다 RecOnData 폴더 스캔
   */
  @Scheduled(fixedDelayString = "PT5M")
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