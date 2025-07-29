package com.sttweb.sttweb.monitor;

import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.service.TbranchService;
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

  @Value("${monitor.admin.emails}")
  private List<String> adminEmails;

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public void onApplicationEvent(ApplicationFailedEvent event) {
    String errorMsg = event.getException().getMessage();
    String time = LocalDateTime.now().format(TS_FMT);

    // 1) 각 지점별 메일
    for (TbranchEntity b : branchService.findAllBranches()) {
      String to = b.getMailAddress();
      if (!StringUtils.hasText(to)) continue;

      String subject = "[서비스 알림] Tomcat 비정상 종료";
      String body = String.format(
          "지점명: %s%nIP: %s%n%nTomcat이 비정상 종료되었습니다.%n원인: %s%n발생시각: %s",
          b.getCompanyName(), b.getPIp(), errorMsg, time
      );

      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setTo(to);
      msg.setSubject(subject);
      msg.setText(body);
      mailSender.send(msg);
    }

    // 2) 관리자 전체메일 (지점명 + IP 목록 포함)
    List<TbranchEntity> branches = branchService.findAllBranches();
    String detail = branches.stream()
        .filter(b -> StringUtils.hasText(b.getPIp()))
        .map(b -> b.getCompanyName() + " (" + b.getPIp() + ")")
        .sorted()
        .collect(Collectors.joining(", "));

    String adminSubject = "[서비스 알림] Tomcat 비정상 종료";
    String adminBody = String.format(
        "Tomcat이 비정상 종료된 지점 목록:%n%s%n%n원인: %s%n발생시각: %s",
        detail, errorMsg, time
    );

    SimpleMailMessage adminMsg = new SimpleMailMessage();
    adminMsg.setTo(adminEmails.toArray(new String[0]));
    adminMsg.setSubject(adminSubject);
    adminMsg.setText(adminBody);
    mailSender.send(adminMsg);

    log.info("비정상 종료 알림 이메일 발송: {}", time);
  }
}
