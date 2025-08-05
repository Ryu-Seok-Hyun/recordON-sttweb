package com.sttweb.sttweb.monitor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.service.TbranchService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceMonitor {

  private final JavaMailSender mailSender;
  private final TbranchService branchService;
  private final RestTemplate restTemplate;

  @Value("${monitor.admin.emails}")
  private String adminEmailCsv;
  @Value("${monitor.debounce.minutes:1}")
  private int debounceMinutes;

  private final AtomicBoolean monitorRunning = new AtomicBoolean(false);
  private final Map<Service, Set<Endpoint>> lastStableDown = new ConcurrentHashMap<>();
  private final Map<Endpoint, LocalDateTime> downSince     = new ConcurrentHashMap<>();
  private final Map<Endpoint, LocalDateTime> recoveredSince= new ConcurrentHashMap<>();
  private static final Cache<String, Boolean> notifyCache = Caffeine.newBuilder().build();

  private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @PostConstruct
  public void init() {
    log.info("ServiceMonitor 초기화, 해시코드: {}", System.identityHashCode(this));
    for (Service svc : Service.values()) {
      lastStableDown.put(svc, new HashSet<>());
    }
    log.info("서비스 모니터 초기화 완료");
  }

  @Scheduled(fixedRateString = "${monitor.fixedRate:60000}",
      initialDelayString = "${monitor.initialDelay:30000}")
  @SchedulerLock(
      name            = "ServiceMonitor_monitorAll",
      lockAtMostFor   = "PT5M",
      lockAtLeastFor  = "PT0S"
  )
  public void monitorAll() {
    if (!monitorRunning.compareAndSet(false, true)) {
      return;
    }
    try {
      LocalDateTime now = LocalDateTime.now();
      List<TbranchEntity> branches = branchService.findAllBranches();

      for (Service svc : Service.values()) {
        if (svc == Service.OPENSEARCH) {
          log.debug("OpenSearch 모니터링 및 알람 생략");
          continue;
        }

        List<Endpoint> endpoints = branches.stream()
            .filter(b -> StringUtils.hasText(b.getPIp()) && !b.getPIp().startsWith("127."))
            .map(b -> new Endpoint(b.getCompanyName(), b.getPIp(), svc))
            .collect(Collectors.toList());

        Set<Endpoint> stableDown = new HashSet<>();
        for (Endpoint ep : endpoints) {
          boolean up = checkService(ep.ip, svc);
          if (!up) {
            downSince.putIfAbsent(ep, now);
            if (debounceMinutes <= 0
                || Duration.between(downSince.get(ep), now).toMinutes() >= debounceMinutes) {
              stableDown.add(ep);
            }
            recoveredSince.remove(ep);
          } else {
            recoveredSince.putIfAbsent(ep, now);
            if (debounceMinutes <= 0
                || Duration.between(recoveredSince.get(ep), now).toMinutes() >= debounceMinutes) {
              downSince.remove(ep);
            }
          }
        }

        Set<Endpoint> prev = lastStableDown.get(svc);
        String hashNow  = getSetHash(stableDown);
        String hashPrev = getSetHash(prev);
        String downKey  = svc.name() + ":DOWN:" + hashNow;
        String recKey   = svc.name() + ":RECOVERED:" + hashPrev;

        if (!stableDown.equals(prev)) {
          log.warn("상태 변경 감지—svc={}, prev={}, now={}", svc.name(), prev, stableDown);
          if (prev.isEmpty() && !stableDown.isEmpty() && notifyCache.getIfPresent(downKey) == null) {
            sendMail(svc, false, stableDown, now);
            notifyCache.put(downKey, true);
          } else if (!prev.isEmpty() && stableDown.isEmpty() && notifyCache.getIfPresent(recKey) == null) {
            sendMail(svc, true, prev, now);
            notifyCache.put(recKey, true);
          } else if (!prev.isEmpty() && !stableDown.isEmpty() && notifyCache.getIfPresent(downKey) == null) {
            sendMail(svc, false, stableDown, now);
            notifyCache.put(downKey, true);
          }
        }
        lastStableDown.put(svc, stableDown);
      }
    } finally {
      monitorRunning.set(false);
    }
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void resetDaily() {
    for (Service svc : Service.values()) {
      lastStableDown.get(svc).clear();
    }
    notifyCache.invalidateAll();
    log.info("데일리 리셋 완료");
  }


  // ─── 신규 추가 메서드 ───

  /**
  +  * 지정된 호스트(host)의 포트가 열려 있는지 확인
  +  */
     private boolean isPortOpen(String host, int port, int timeoutMillis) {
       try (Socket sock = new Socket()) {
           sock.connect(new InetSocketAddress(host, port), timeoutMillis);
           return true;
         } catch (IOException e) {
           return false;
         }
     }

  /**
   * HTTP GET 요청으로 200 OK를 반환하는지 확인
   */
  private boolean isHttp200(String url) {
    try {
      ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
      return resp.getStatusCodeValue() == 200;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * HTTP 응답이 500 미만(2xx, 4xx)이면 정상으로 처리
   */
  private boolean isHttpHealthy(String url) {
    try {
      ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
       int code = resp.getStatusCodeValue();
       // 2xx 또는 4xx 범위를 정상으로 간주
      return (code >= 200 && code < 300) || (code >= 400 && code < 500);
    } catch (HttpClientErrorException e) {
      // 4xx 에러지만 서버가 응답함 → 정상
      return true;
    } catch (Exception e) {
      // 5xx, 연결 실패 등 → 비정상
      return false;
    }
  }

  /**
   * 포트 → HTTP 순으로 로컬에서 서비스 가용성 체크
   */
  private boolean checkService(String host, Service svc) {

    // 1) 포트 체크 (127.0.0.1 → host)
        if (!isPortOpen(host, svc.port, 1_000)) {
          log.warn("{} 포트 열림 실패: host={}, port={}", svc, host, svc.port);
          return false;
          }

      // OpenSearch는 포트만 열려 있으면 정상 처리
          if (svc == Service.OPENSEARCH) {
          return true;
        }

    // 2) HTTP 상태 체크 (127.0.0.1 → host)
        String path = svc.healthPath != null ? svc.healthPath : "/";
        String url  = "http://" + host + ":" + svc.port + path;

    boolean ok = isHttpHealthy(url);    // <-- 여기만 변경
    if (!ok) {
      log.warn("{} HTTP 응답 실패: url={}", svc, url);
    }
    return ok;
  }

  // ────────────────────────


  private String getSetHash(Set<Endpoint> set) {
    if (set == null) return "0";
    return Integer.toHexString(
        set.stream().map(Object::hashCode).sorted()
            .reduce(0, (a, b) -> 31 * a + b)
    );
  }

  private void sendMail(Service svc, boolean recovered, Set<Endpoint> eps, LocalDateTime now) {
    String detail = eps.stream()
        .map(ep -> ep.branchName + " (" + ep.ip + ")")
        .sorted().collect(Collectors.joining(", "));
    String time = now.format(TS_FMT);
    String subj = String.format("[서비스 알림] %s %s [%s]",
        svc.label, recovered ? "복구" : "장애", detail);
    String body = String.format("서비스: %s%n상태: %s%n시각: %s%n발생지점: %s",
        svc.label, recovered ? "복구되었습니다." : "장애 발생", time, detail);

    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(adminEmailList());
    msg.setSubject(subj);
    msg.setText(body);
    try {
      mailSender.send(msg);
      log.info("메일발송 [{}] {} - {}", recovered ? "RECOVERED" : "DOWN", svc.label, detail);
    } catch (MailException ex) {
      log.error("메일 전송 실패: {}", ex.getMessage(), ex);
    }
  }

  private String[] adminEmailList() {
    return Arrays.stream(adminEmailCsv.split(","))
        .map(String::trim).filter(StringUtils::hasText)
        .distinct().toArray(String[]::new);
  }

  private enum Service {
    APACHE(39080, "XAMPP(Apache)", "/server-status?auto"),
    TOMCAT(39090, "Tomcat", "/actuator/health"),
    OPENSEARCH(9200, "OpenSearch", null),
    STT(39500, "STT Service", "/health");

    final int port;
    final String label;
    final String healthPath;
    Service(int p, String l, String h) { port = p; label = l; healthPath = h; }
  }

  private record Endpoint(String branchName, String ip, Service svc) {}
}
