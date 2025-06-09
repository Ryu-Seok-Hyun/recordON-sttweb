package com.sttweb.sttweb.service;

import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.repository.TbranchRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@EnableScheduling
public class BranchHealthCheckService {

  private static final Logger logger = LoggerFactory.getLogger(BranchHealthCheckService.class);

  private final TbranchRepository branchRepository;

  /**
   * 1분마다 모든 지점의 Tomcat 서버 상태를 체크하고 DB에 저장
   * cron: "0 * * * * *" → 매 분 0초에 실행
   */
  @Scheduled(cron = "0 * * * * *")
  @Transactional
  public void checkAllBranchesHealth() {
    List<TbranchEntity> allBranches = branchRepository.findAll();

    for (TbranchEntity branch : allBranches) {
      String ip   = branch.getPIp();
      String port = branch.getPPort();

      // p_ip 또는 pb_ip가 비어 있으면 무시
      if (ip == null || ip.isBlank() || port == null || port.isBlank()) {
        continue;
      }

      boolean previousAlive = Boolean.TRUE.equals(branch.getIsAlive());
      boolean currentAlive;
      LocalDateTime now = LocalDateTime.now();
      String urlString = "http://" + ip + ":" + port + "/"; // 기본 루트 경로로 GET 요청

      Integer responseTimeMs = null;
      String  errorMessage    = null;

      try {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000); // 2초
        conn.setReadTimeout(2000);

        long start = System.currentTimeMillis();
        conn.connect();
        int code = conn.getResponseCode();
        long end = System.currentTimeMillis();

        responseTimeMs = (int) (end - start);
        currentAlive = (code >= 200 && code < 400);

        conn.disconnect();

      } catch (IOException ioe) {
        currentAlive = false;
        errorMessage = ioe.getClass().getSimpleName() + ": " + ioe.getMessage();
      }

      // 1) last_health_check 갱신
      branch.setLastHealthCheck(now);

      // 2) is_alive 상태 갱신
      branch.setIsAlive(currentAlive);

      // 3) “이전엔 살아있었는데 지금 죽었다면” → last_downtime 기록
      if (previousAlive && !currentAlive) {
        branch.setLastDowntime(now);
        logger.info("BranchID={}({}:{}) TOMCAT 서버 DOWN 감지. time={}",
            branch.getBranchSeq(), ip, port, now);
      }

      // 4) “이전엔 죽어있었는데 지금 살아났다면” → last_downtime는 그대로 두거나(과거 다운 기록 유지),
      //    특정 로직 필요하다면 여기서 처리(예: last_downtime=null)
      if (!previousAlive && currentAlive) {
        // 예시: 과거 다운 기록을 유지하되, 별도 로그만 남긴다:
        logger.info("BranchID={}({}:{}) TOMCAT 서버 RECOVER 감지. time={}",
            branch.getBranchSeq(), ip, port, now);
      }

      // 5) 엔티티 저장 (변경 감지) → @Transactional 덕분에 끝날 때 자동으로 flush
      branchRepository.save(branch);
    }
  }
}
