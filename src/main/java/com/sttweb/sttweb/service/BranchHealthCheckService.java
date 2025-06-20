package com.sttweb.sttweb.service;

import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.repository.TbranchRepository;
import jakarta.transaction.Transactional;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BranchHealthCheckService {

  private static final Logger log = LoggerFactory.getLogger(BranchHealthCheckService.class);
  private static final int TIMEOUT_MS = 2_000;

  private final TbranchRepository branchRepo;

  @Scheduled(cron = "0 * * * * *")
  @Transactional
  public void checkAllBranchesHealth() {
    LocalDateTime now = LocalDateTime.now();

    // 1) 오늘 나의 호스트 IP 리스트를 미리 수집
    Set<String> localIps = detectLocalIpv4Addresses();

    for (TbranchEntity b : branchRepo.findAll()) {
      // 2) 사설IP가 내 호스트 NIC에 바인딩되어 있으면 로컬 모드
      boolean runningOnBranch = localIps.contains(b.getPbIp());

      String ip  = runningOnBranch ? b.getPbIp()  : b.getPIp();
      String port = runningOnBranch ? b.getPbPort() : b.getPPort();

      if (!StringUtils.hasText(ip) || !StringUtils.hasText(port)) {
        log.debug("Branch {}: IP/포트 미설정, 스킵", b.getBranchSeq());
        continue;
      }

      boolean prevAlive = Boolean.TRUE.equals(b.getIsAlive());
      PingResult result = socketPing(ip, Integer.parseInt(port));

      if (!result.alive) {
        result = actuatorHealthCheck(ip, port);
      }
      boolean currAlive = result.alive;

      // 3) 엔티티 업데이트
      b.setLastHealthCheck(now);
      b.setIsAlive(currAlive);
      if (prevAlive && !currAlive) {
        b.setLastDowntime(now);
        log.warn("DOWN ▶ {}({}:{}), err={}, rt={}ms",
            b.getCompanyName(), ip, port,
            result.errorMessage, result.responseTimeMs);
      } else if (!prevAlive && currAlive) {
        log.info("RECOVER ▶ {}({}:{})", b.getCompanyName(), ip, port);
      }

      branchRepo.save(b);
    }
  }

  /** 내 호스트에 바인딩된 IPv4 주소 전부 수집 */
  private Set<String> detectLocalIpv4Addresses() {
    Set<String> ips = new HashSet<>();
    try {
      for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        if (nif.isLoopback() || !nif.isUp()) continue;
        for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
          if (addr instanceof Inet4Address) {
            ips.add(addr.getHostAddress());
          }
        }
      }
    } catch (SocketException e) {
      log.error("로컬 IP 감지 실패", e);
    }
    return ips;
  }

  private PingResult socketPing(String ip, int port) {
    long start = System.currentTimeMillis();
    try (Socket s = new Socket()) {
      s.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
      return new PingResult(true, (int)(System.currentTimeMillis() - start), null);
    } catch (IOException e) {
      return new PingResult(false, (int)(System.currentTimeMillis() - start),
          e.getClass().getSimpleName() + ":" + e.getMessage());
    }
  }

  private PingResult actuatorHealthCheck(String ip, String port) {
    String url = "http://" + ip + ":" + port + "/actuator/health";
    long start = System.currentTimeMillis();
    try {
      HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
      c.setRequestMethod("GET");
      c.setConnectTimeout(TIMEOUT_MS);
      c.setReadTimeout(TIMEOUT_MS);
      int code = c.getResponseCode();
      c.disconnect();
      return new PingResult(code == 200,
          (int)(System.currentTimeMillis() - start),
          code == 200 ? null : "HTTP " + code);
    } catch (IOException e) {
      return new PingResult(false, (int)(System.currentTimeMillis() - start),
          e.getClass().getSimpleName() + ":" + e.getMessage());
    }
  }

  private static class PingResult {
    final boolean alive;
    final Integer responseTimeMs;
    final String  errorMessage;
    PingResult(boolean a, Integer rt, String err) {
      this.alive = a; this.responseTimeMs = rt; this.errorMessage = err;
    }
  }
}
