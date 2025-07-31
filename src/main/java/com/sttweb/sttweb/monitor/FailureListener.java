package com.sttweb.sttweb.monitor;

import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.service.TbranchService;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class FailureListener implements ApplicationListener<ApplicationFailedEvent> {

  private final JavaMailSender mailSender;
  private final TbranchService branchService;
  // 한 번만 알림을 보내기 위한 플래그
  private static volatile boolean notificationSent = false;

  @Value("${monitor.admin.emails:}")
  private String adminEmailsProp;

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public void onApplicationEvent(ApplicationFailedEvent event) {
    // 이미 보냈으면 중복 스킵
    if (notificationSent) {
      log.info("Failure notification already sent, skipping duplicate");
      return;
    }
    notificationSent = true;

    String errorMsg = event.getException().getMessage();
    String time = LocalDateTime.now().format(TS_FMT);

    // 1) 지점별 메일
    for (TbranchEntity b : branchService.findAllBranches()) {
      String to = b.getMailAddress();
      if (!StringUtils.hasText(to)) continue;

      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setTo(to);
      msg.setSubject("[서비스 알림] Tomcat 비정상 종료");
      msg.setText(String.format(
          "지점명: %s%nIP: %s%n%nTomcat이 비정상 종료되었습니다.%n원인: %s%n발생시각: %s",
          b.getCompanyName(), b.getPIp(), errorMsg, time
      ));
      mailSender.send(msg);
    }

    // 2) 관리자 전체메일
    if (StringUtils.hasText(adminEmailsProp)) {
      List<String> admins = Arrays.stream(adminEmailsProp.split("\\s*,\\s*"))
          .filter(StringUtils::hasText)
          .distinct()
          .collect(Collectors.toList());

      String detail = branchService.findAllBranches().stream()
          .filter(b -> StringUtils.hasText(b.getPIp()))
          .map(b -> b.getCompanyName() + " (" + b.getPIp() + ")")
          .sorted()
          .collect(Collectors.joining(", "));

      SimpleMailMessage adminMsg = new SimpleMailMessage();
      adminMsg.setTo(admins.toArray(new String[0]));
      adminMsg.setSubject("[서비스 알림] Tomcat 비정상 종료");
      adminMsg.setText(String.format(
          "Tomcat이 비정상 종료된 지점:%n%s%n%n원인: %s%n발생시각: %s",
          detail, errorMsg, time
      ));
      mailSender.send(adminMsg);
    }

    log.info("비정상 종료 알림 이메일 발송: {}", time);
  }
}
