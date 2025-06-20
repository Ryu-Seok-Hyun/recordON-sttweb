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

  @PostConstruct
  public void initializeStatusCache() {
    log.info("▶ 서비스 상태 캐시 초기화 (실제 상태 기반)");
    branchService.findAllBranches().forEach(branch -> {
      String ip = branch.getPIp();
      if (ip == null || ip.isBlank()) return;

      for (Service svc : Service.values()) {
        String key = buildKey(ip, svc.port);
        boolean upNow = isReachable(ip, svc.port);
        serviceStatusCache.put(key, upNow);
        log.debug("  - {} 초기상태: {}", key, upNow ? "UP" : "DOWN");
        // (원한다면) DB에도 동기화
        branchService.updateHealthStatus(ip, svc.port, upNow);
      }
    });
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

    for (TbranchEntity branch : branchService.findAllBranches()) {
      String ip = branch.getPIp();
      if (ip == null || ip.isBlank()) continue;

      for (Service svc : Service.values()) {
        String key      = buildKey(ip, svc.port);
        boolean upNow   = isReachable(ip, svc.port);
        boolean upBefore= serviceStatusCache.getOrDefault(key, upNow);

        if (upNow != upBefore) {
          // 1) 캐시·DB 업데이트
          serviceStatusCache.put(key, upNow);
          branchService.updateHealthStatus(ip, svc.port, upNow);

          // 2) 개별 알림 메일 전송
          sendSingleMail(branch.getCompanyName(), ip, svc, upNow);

          log.info("[{}] {}:{} 상태변경 {}→{}",
              svc.label, ip, svc.port,
              upBefore ? "UP" : "DOWN",
              upNow     ? "UP" : "DOWN");
        }
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

  private void sendSingleMail(String branchName, String host, Service svc, boolean recovered) {
    String time    = LocalDateTime.now().format(TS_FMT);
    String status  = recovered ? "복구되었습니다." : "중지되었습니다.";
    String subject = String.format("[서비스 알림] %s %s", svc.label, recovered ? "복구" : "장애");
    String body    = String.format(
        "지점명: %s%nIP: %s:%d%n%n%s %s%n발생시각: %s",
        branchName, host, svc.port,
        svc.label, status,
        time
    );

    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(adminEmails.toArray(new String[0]));  // 한 번만 호출
    msg.setSubject(subject);
    msg.setText(body);

    try {
      mailSender.send(msg);
      log.info("▶ 개별 메일 발송 [{}]: {} - {}",
          recovered ? "RECOVER" : "DOWN", branchName, svc.label);
    } catch (MailException ex) {
      // Gmail 한도 초과 감지
      if (ex.getCause() instanceof SMTPSendFailedException
          && ex.getMessage().contains("550-5.4.5 Daily user sending limit exceeded")) {
        emailEnabled = false;
        log.error("‼ Gmail 일일 한도 초과, 이후 메일 발송 중단");
      } else {
        log.error("메일 전송 실패: {}", ex.getMessage(), ex);
      }
    }
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
    APACHE     (39080, "XAMPP(Apache)"),
    TOMCAT     (39090, "Tomcat"),
    OPENSEARCH (9200,  "OpenSearch");

    final int    port;
    final String label;
    Service(int port, String label) { this.port = port; this.label = label; }
  }
}
