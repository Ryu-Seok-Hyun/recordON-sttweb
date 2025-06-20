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

@Component
@Slf4j
@RequiredArgsConstructor
public class ShutdownListener implements ApplicationListener<ContextClosedEvent> {

  private final JavaMailSender mailSender;
  private final TbranchService  branchService;

  @Value("${monitor.admin.emails}")
  private List<String> adminEmails;

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public void onApplicationEvent(ContextClosedEvent event) {
    String time = LocalDateTime.now().format(TS_FMT);

    // 1) 각 지점별 메일
    for (TbranchEntity b : branchService.findAllBranches()) {
      String to         = b.getMailAddress();
      String branchName = b.getCompanyName();
      String ip         = b.getPIp();
      String subject    = "[서비스 알림] Tomcat 정상 종료";
      String body       = String.format(
          "지점명: %s%nIP: %s%n%nTomcat이 정상 종료되었습니다.%n종료시각: %s",
          branchName, ip, time
      );

      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setTo(to);
      msg.setSubject(subject);
      msg.setText(body);
      mailSender.send(msg);
    }

    // 2) 관리자 전체메일
    String adminSubject = "[서비스 알림] Tomcat 정상 종료 (전체)";
    String adminBody    = "모든 지점의 Tomcat이 정상 종료되었습니다.\n종료시각: " + time;

    for (String admin : adminEmails) {
      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setTo(admin);
      msg.setSubject(adminSubject);
      msg.setText(adminBody);
      mailSender.send(msg);
    }

    log.info("종료 알림 이메일 발송: {}", time);
  }
}
