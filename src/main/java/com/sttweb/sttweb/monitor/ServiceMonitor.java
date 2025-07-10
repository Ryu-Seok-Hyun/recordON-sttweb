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
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceMonitor {

  private final JavaMailSender mailSender;
  private final TbranchService branchService;

  @Value("${monitor.admin.emails}")
  private String adminEmailCsv;    // List<String> 대신 comma-separated String

  private List<String> adminEmails() {
    return Arrays.stream(adminEmailCsv.split(","))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .collect(Collectors.toList());
  }

  @Value("${monitor.admin.emails}")
  private List<String> adminEmails;

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // 서비스별 상태 캐시 (서비스+죽은 지점명 List)
  private final Map<Service, Set<String>> lastDownBranchNames = new ConcurrentHashMap<>();

  // 한도 초과 시 비활성화
  private volatile boolean emailEnabled = true;

  // 내부 레코드
  private record Endpoint(String branchName, String ip, int port, Service service) {}

  @PostConstruct
  public void initializeStatusCache() {
    for (Service svc : Service.values()) {
      lastDownBranchNames.put(svc, new HashSet<>());
    }
    log.info("서비스 모니터 캐시 초기화 완료");
  }

  @Scheduled(fixedRateString = "${monitor.fixedRate:60000}", initialDelayString = "${monitor.initialDelay:30000}")
  public void monitorAll() {
    if (!emailEnabled) {
      log.warn("메일 발송 비활성화 상태, 스킵");
      return;
    }

    List<TbranchEntity> branches = branchService.findAllBranches();
    for (Service svc : Service.values()) {
      // p_ip로만 체크 (127.0.0.1 제외)
      List<Endpoint> endpoints = branches.stream()
          .filter(b -> b.getPIp() != null && !b.getPIp().isBlank() && !b.getPIp().startsWith("127."))
          .map(b -> new Endpoint(b.getCompanyName(), b.getPIp(), svc.port, svc))
          .collect(Collectors.toList());

      // 다운된 지점명 리스트
      Set<String> currentDownBranchNames = endpoints.stream()
          .filter(ep -> !isReachable(ep.ip, ep.port))
          .map(ep -> ep.branchName)
          .collect(Collectors.toSet());

      Set<String> beforeDownBranchNames = lastDownBranchNames.getOrDefault(svc, new HashSet<>());

      // 장애 감지 (DOWN된 지점명 집합이 바뀌었을 때만)
      if (!currentDownBranchNames.equals(beforeDownBranchNames)) {
        // 복구된 지점명
        Set<String> recovered = new HashSet<>(beforeDownBranchNames);
        recovered.removeAll(currentDownBranchNames);

        // 새롭게 장애된 지점명
        Set<String> newlyDown = new HashSet<>(currentDownBranchNames);
        newlyDown.removeAll(beforeDownBranchNames);

        if (!newlyDown.isEmpty()) {
          sendServiceMail(svc, false, newlyDown, LocalDateTime.now());
        }
        if (!recovered.isEmpty()) {
          sendServiceMail(svc, true, recovered, LocalDateTime.now());
        }
        // 상태 저장
        lastDownBranchNames.put(svc, currentDownBranchNames);
      }

      // DB 상태 동기화
      endpoints.forEach(ep -> branchService.updateHealthStatus(ep.ip, ep.port, !currentDownBranchNames.contains(ep.branchName)));
    }
  }

  private void sendServiceMail(Service svc, boolean recovered, Set<String> branchNames, LocalDateTime now) {
    if (branchNames.isEmpty()) return;
    String time = now.format(TS_FMT);
    String status = recovered ? "복구" : "장애";
    String subject = String.format("[서비스 알림] %s %s", svc.label, status);

    String body = String.format(
        "서비스: %s%n상태: %s%n발생시각: %s%n영향 지점: %s",
        svc.label, recovered ? "복구되었습니다." : "중지되었습니다.", time,
        String.join(", ", branchNames)
    );

    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(adminEmails.toArray(new String[0]));
    msg.setSubject(subject);
    msg.setText(body);

    try {
      mailSender.send(msg);
      log.info("▶ 메일발송 [{}] {} - {}", status, svc.label, branchNames);
    } catch (MailException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof SMTPSendFailedException
          && ex.getMessage().contains("Daily user sending limit exceeded")) {
        emailEnabled = false;
        log.error("‼ Gmail 일일 한도 초과, 이후 메일발송 중단");
      } else {
        log.error("메일 전송 실패: {}", ex.getMessage(), ex);
      }
    }
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void resetEmailFlag() {
    emailEnabled = true;
    log.info("▶ 이메일 발송 플래그 자정 초기화");
  }

  private boolean isReachable(String host, int port) {
    try (Socket s = new Socket()) {
      s.connect(new InetSocketAddress(host, port), 2000);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  // 서비스 종류
  private enum Service {
    APACHE(39080, "XAMPP(Apache)"),
    TOMCAT(39090, "Tomcat"),
    OPENSEARCH(9200, "OpenSearch"),
    STT(39500, "STT Service");

    final int port;
    final String label;
    Service(int port, String label) {
      this.port = port;
      this.label = label;
    }
  }
}
