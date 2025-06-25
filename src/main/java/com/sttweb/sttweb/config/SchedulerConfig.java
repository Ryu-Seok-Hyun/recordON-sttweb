package com.sttweb.sttweb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(5);
    scheduler.setThreadNamePrefix("scheduled-task-");
    // 스레드 팩토리에서 컨텍스트 클래스로더를 웹앱 로더로 지정
    scheduler.setThreadFactory(runnable -> {
      Thread thread = new Thread(runnable);
      thread.setContextClassLoader(
          SchedulerConfig.class.getClassLoader()
      );
      return thread;
    });
    // (선택) 애플리케이션 종료 시 스레드풀도 깨끗하게 종료
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.initialize();

    registrar.setTaskScheduler(scheduler);
  }
}
