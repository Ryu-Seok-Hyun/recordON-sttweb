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
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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


  private String getSetHash(Set<Endpoint> set) {
    // set이 null이면 빈값 반환
    if (set == null) return "0";
    // Set의 순서가 달라도 같은 hash가 나오게, 정렬해서 hash코드 생성
    return Integer.toHexString(
        set.stream()
            .map(Object::hashCode)
            .sorted()
            .reduce(0, (a, b) -> 31 * a + b)
    );
  }

  @PostConstruct
  public void init() {
    log.info("ServiceMonitor Bean 해시코드: {}", System.identityHashCode(this));
    for (Service svc : Service.values()) {
      lastStableDown.put(svc, new HashSet<>());
    }
    log.info("ServiceMonitor Bean 해시코드: {}", System.identityHashCode(this));
    log.info("서비스 모니터 초기화 완료");
  }


  @Scheduled(fixedRateString = "${monitor.fixedRate:60000}",
      initialDelayString = "${monitor.initialDelay:30000}")
  @SchedulerLock(
      name            = "ServiceMonitor_monitorAll",    // 락 키 (유니크)
      lockAtMostFor   = "PT5M",                         // 최대 5분간 락을 보유
      lockAtLeastFor  = "PT0S"                          // 최소 보유 시간 (즉시 해제)
  )
  public void monitorAll() {
    log.info("monitorAll called: " + LocalDateTime.now());
    if (!monitorRunning.compareAndSet(false, true)) {
      log.debug("monitorAll: 이전 실행 중, 스킵");
      return;
    }
    try {
      LocalDateTime now = LocalDateTime.now();
      List<TbranchEntity> branches = branchService.findAllBranches();

      for (Service svc : Service.values()) {
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

        String setHash = getSetHash(stableDown);
        String prevHash = getSetHash(prev);

        String downKey = svc.name() + ":DOWN:" + setHash;
        String recKey  = svc.name() + ":RECOVERED:" + prevHash;

        // LOG: 조건별 분기 로그
        if (!stableDown.equals(prev)) {
          log.warn(">>> 상태 변경 감지 - svc={}, prev={}, stableDown={}", svc.name(), prev, stableDown);
          if (prev.isEmpty() && !stableDown.isEmpty()) {
            log.warn(">>> 장애 시작 분기 진입 - downKey={}", downKey);
            if (notifyCache.getIfPresent(downKey) == null) {
              sendMail(svc, false, stableDown, now);
              notifyCache.put(downKey, true);
            }
          } else if (!prev.isEmpty() && stableDown.isEmpty()) {
            log.warn(">>> 복구 분기 진입 - recKey={}", recKey);
            if (notifyCache.getIfPresent(recKey) == null) {
              sendMail(svc, true, prev, now);
              notifyCache.put(recKey, true);
            }
          } else if (!prev.isEmpty() && !stableDown.isEmpty()) {
            log.warn(">>> 장애 대상 변경 분기 진입 - downKey={}", downKey);
            if (notifyCache.getIfPresent(downKey) == null) {
              sendMail(svc, false, stableDown, now);
              notifyCache.put(downKey, true);
            }
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
    log.info("데일리 리셋 완료");
  }

  private final Map<Endpoint, Integer> failureCount = new ConcurrentHashMap<>();
  private static final int MAX_FAILS = 3;  // 3회 연속 실패 시에만 장애로 판단

  private boolean checkService(String host, Service svc) {
    String url = svc == Service.OPENSEARCH
        ? "http://" + host + ":" + svc.port + "/"
        : "http://" + host + ":" + svc.port + svc.healthPath;

    try {
      ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
      boolean ok = resp.getStatusCode().is2xxSuccessful();
      if (ok) {
        failureCount.put(new Endpoint("", host, svc), 0); // 성공시 실패카운트 초기화
      }
      return ok;
    } catch (Exception e) {
      int fails = failureCount.getOrDefault(new Endpoint("", host, svc), 0) + 1;
      failureCount.put(new Endpoint("", host, svc), fails);
      log.warn("{} 체크 실패 {}회: host={}, err={}", svc, fails, host, e.getMessage());
      // N회 이상 연속 실패한 경우에만 false 리턴
      return fails >= MAX_FAILS ? false : true;
    }
  }

  private String joinDetail(Set<Endpoint> list) {
    return list.stream()
        .map(ep -> ep.branchName + " (" + ep.ip + ")")
        .sorted()
        .collect(Collectors.joining(", "));
  }

  private void sendMail(Service svc, boolean recovered, Set<Endpoint> eps, LocalDateTime now) {
    String detail = joinDetail(eps);
    String time   = now.format(TS_FMT);
    String subj   = String.format("[서비스 알림] %s %s [%s]",
        svc.label, recovered ? "복구" : "장애", detail);

    // 로그 추가
    log.warn(">>> sendMail 실행 - svc={}, recovered={}, subj={}, to={}, eps={}", svc.name(), recovered, subj, Arrays.toString(adminEmailList()), eps);

    String body   = String.format("서비스: %s%n상태: %s%n시각: %s%n발생지점: %s",
        svc.label, recovered ? "복구되었습니다." : "장애 발생", time, detail);

    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(adminEmailList());
    msg.setSubject(subj);
    msg.setText(body);
    try {
      mailSender.send(msg);
      log.info("메일발송 [{}] {} - {}", recovered ? "RECOVERED":"DOWN", svc.label, detail);
    } catch (MailException ex) {
      log.error("메일 전송 실패: {}", ex.getMessage(), ex);
    }
  }

  private String[] adminEmailList() {
    String[] arr = Arrays.stream(adminEmailCsv.split(","))
        .map(String::trim).filter(StringUtils::hasText)
        .distinct().toArray(String[]::new);
    log.warn(">>> adminEmailList: {}", Arrays.toString(arr));
    return arr;
  }

  private enum Service {
    APACHE(39080,"XAMPP(Apache)","/server-status?auto"),
    TOMCAT(39090,"Tomcat","/actuator/health"),
    OPENSEARCH(9200,"OpenSearch",null),
    STT(39500,"STT Service","/health");

    final int port; final String label; final String healthPath;
    Service(int p, String l, String h){ port=p; label=l; healthPath=h; }
  }

  private record Endpoint(String branchName, String ip, Service svc) {}
}
