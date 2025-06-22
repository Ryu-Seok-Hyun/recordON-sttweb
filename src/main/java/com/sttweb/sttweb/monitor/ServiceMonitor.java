package com.sttweb.sttweb.monitor;

import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.service.TbranchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceMonitor {

  private final JavaMailSender mailSender;
  private final TbranchService branchService;

  @Value("${monitor.admin.emails}")
  private List<String> adminEmails;

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // Key: "ip:port" → last known status (true=UP, false=DOWN)
  private final Map<String, Boolean> serviceStatusCache = new ConcurrentHashMap<>();

  // 하루 한도 초과 시 false로 내려가고, 자정에 다시 true로 복구됩니다.
  private volatile boolean emailEnabled = true;

  // --- [수정] Endpoint를 표현하기 위한 내부 레코드(Record) 정의 ---
  private record Endpoint(String ip, int port, Service service) {}

  @PostConstruct
  public void initializeStatusCache() {
    log.info("▶ 서비스 상태 캐시 초기화 (실제 상태 기반)");
    for (Endpoint endpoint : getUniqueEndpoints()) {
      boolean upNow = isReachable(endpoint.ip, endpoint.port);
      serviceStatusCache.put(buildKey(endpoint.ip, endpoint.port), upNow);
      log.debug("  - {} ({}) 초기상태: {}", buildKey(endpoint.ip, endpoint.port), endpoint.service.label, upNow ? "UP" : "DOWN");
      branchService.updateHealthStatus(endpoint.ip, endpoint.port, upNow);
    }
    log.info("▶ 서비스 상태 캐시 초기화 완료");
  }

  /** 모니터링 주기(기본 60초) */
  @Scheduled(fixedRateString = "${monitor.fixedRate:60000}",
      initialDelayString = "${monitor.initialDelay:30000}")
  public void monitorAll() {
    if (!emailEnabled) {
      log.warn("메일 발송 비활성화 상태, 이번 주기 스킵");
      return;
    }

    // --- [수정] 지점(Branch) 대신 고유 엔드포인트(Endpoint)를 기준으로 순회 ---
    for (Endpoint endpoint : getUniqueEndpoints()) {
      String key = buildKey(endpoint.ip, endpoint.port);
      boolean upNow = isReachable(endpoint.ip, endpoint.port);
      boolean upBefore = serviceStatusCache.getOrDefault(key, upNow);

      if (upNow != upBefore) {
        // 1) 캐시·DB 업데이트
        serviceStatusCache.put(key, upNow);
        branchService.updateHealthStatus(endpoint.ip, endpoint.port, upNow);

        // --- [수정] 단일 메일이 아닌, 통합 메일 전송 로직 호출 ---
        sendConsolidatedMail(endpoint, upNow);

        log.info("[{}] {}:{} 상태변경 {}→{}",
            endpoint.service.label, endpoint.ip, endpoint.port,
            upBefore ? "UP" : "DOWN",
            upNow ? "UP" : "DOWN");
      }
    }
  }

  /**
   * [신규] 모든 지점 정보를 바탕으로 고유한 모니터링 대상(IP:Port) 목록을 생성합니다.
   */
  private Set<Endpoint> getUniqueEndpoints() {
    Set<Endpoint> endpoints = new HashSet<>();
    for (TbranchEntity branch : branchService.findAllBranches()) {
      String ip = branch.getPIp();
      if (ip != null && !ip.isBlank()) {
        for (Service svc : Service.values()) {
          endpoints.add(new Endpoint(ip, svc.port, svc));
        }
      }
    }
    return endpoints;
  }

  /**
   * [수정] 단일 이벤트에 대해 영향을 받는 모든 지점을 묶어 한 통의 메일만 발송합니다.
   */
  private void sendConsolidatedMail(Endpoint endpoint, boolean recovered) {
    String time = LocalDateTime.now().format(TS_FMT);
    String status = recovered ? "복구되었습니다." : "중지되었습니다.";
    String subject = String.format("[서비스 알림] %s %s", endpoint.service.label, recovered ? "복구" : "장애");

    // 해당 IP와 Port를 사용하는 모든 지점의 이름을 찾습니다.
    List<TbranchEntity> affectedBranches = branchService.findByIpAndPort(endpoint.ip, String.valueOf(endpoint.port));
    String affectedBranchNames = affectedBranches.stream()
        .map(TbranchEntity::getCompanyName)
        .collect(Collectors.joining(", "));

    if (affectedBranchNames.isEmpty()) {
      affectedBranchNames = "(알 수 없는 지점)";
    }

    String body = String.format(
        "서비스: %s%n" +
            "서버: %s:%d%n" +
            "상태: %s%n" +
            "발생시각: %s%n\n" +
            "영향받는 지점: %s",
        endpoint.service.label, endpoint.ip, endpoint.port,
        status,
        time,
        affectedBranchNames
    );

    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(adminEmails.toArray(new String[0]));
    msg.setSubject(subject);
    msg.setText(body);

    try {
      mailSender.send(msg);
      log.info("▶ 통합 메일 발송 [{}]: {} -> {}",
          recovered ? "RECOVER" : "DOWN", endpoint.ip + ":" + endpoint.port, affectedBranchNames);
    } catch (MailException ex) {
      if (ex.getCause() instanceof SMTPSendFailedException
          && ex.getMessage().contains("550-5.4.5 Daily user sending limit exceeded")) {
        emailEnabled = false;
        log.error("‼ Gmail 일일 한도 초과, 이후 메일 발송 중단");
      } else {
        log.error("메일 전송 실패: {}", ex.getMessage(), ex);
      }
    }
  }

  /**
   * 자정에 emailEnabled 플래그를 다시 true로 복구.
   */
  @Scheduled(cron = "0 0 0 * * *")
  public void resetEmailFlag() {
    emailEnabled = true;
    log.info("▶ 이메일 발송 플래그 자정에 초기화");
  }

  private boolean isReachable(String host, int port) {
    try (Socket s = new Socket()) {
      s.connect(new InetSocketAddress(host, port), 2_000);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private String buildKey(String host, int port) {
    return host + ":" + port;
  }

  private enum Service {
    APACHE(39080, "XAMPP(Apache)"),
    TOMCAT(39090, "Tomcat"),
    OPENSEARCH(9200, "OpenSearch");

    final int port;
    final String label;

    Service(int port, String label) {
      this.port = port;
      this.label = label;
    }
  }
}