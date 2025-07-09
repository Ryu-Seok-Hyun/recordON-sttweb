package com.sttweb.sttweb.monitor;

import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.service.TbranchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class ShutdownListener implements ApplicationListener<ContextClosedEvent> {

  private final JavaMailSender  mailSender;
  private final TbranchService  branchService;

  /** application.properties → monitor.admin.emails=aaa@bb.com,ccc@dd.com … */
  @Value("${monitor.admin.emails:}")               // ← 값이 없으면 빈 문자열
  private String adminEmailsProp;                  // ※ List 로 주입-받으면 null 이 나올 수 있음

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public void onApplicationEvent(ContextClosedEvent event) {

    String now = LocalDateTime.now().format(TS_FMT);

    /* ── 1) 지점별 메일 ─────────────────────────────────────────────── */
    for (TbranchEntity b : branchService.findAllBranches()) {

      // 지점의 메일 주소가 없으면 skip
      if (!StringUtils.hasText(b.getMailAddress())) continue;

      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setTo(b.getMailAddress());
      msg.setSubject("[서비스 알림] Tomcat 정상 종료");
      msg.setText(String.format(
          "지점명: %s%nIP: %s%n%nTomcat이 정상 종료되었습니다.%n종료시각: %s",
          b.getCompanyName(), b.getPIp(), now
      ));
      mailSender.send(msg);
    }

    /* ── 2) 관리자 메일 ─────────────────────────────────────────────── */
    if (StringUtils.hasText(adminEmailsProp)) {
      String[] admins = adminEmailsProp.split("\\s*,\\s*");   // 콤마 분리

      for (String admin : admins) {
        if (!StringUtils.hasText(admin)) continue;           // 빈 값 skip

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(admin);
        msg.setSubject("[서비스 알림] Tomcat 정상 종료 (전체)");
        msg.setText("모든 지점의 Tomcat이 정상 종료되었습니다.\n종료시각: " + now);
        mailSender.send(msg);
      }
    }

    log.info("Shutdown mail sent at {}", now);
  }
}
