package com.sttweb.sttweb.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class MonitoringConfig implements SchedulingConfigurer {

  /**
   * 스프링이 관리하는 스케줄러 빈
   * - poolSize: 5
   * - shutdown 시 대기 시간 10초
   */
  @Bean
  public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
    ts.setPoolSize(5);
    ts.setThreadNamePrefix("monitor-");
    ts.setWaitForTasksToCompleteOnShutdown(true);
    ts.setAwaitTerminationSeconds(10);
    return ts;
  }

  /**
   * 톰캣 종료 시 내부 selector 스레드도 함께 종료되도록 HttpClient 빈 정의
   */
  @Bean
  public HttpClient monitoringHttpClient(ThreadPoolTaskScheduler taskScheduler) {
    return HttpClient.newBuilder()
        .executor(taskScheduler)               // 스케줄러 자체를 Executor로 사용
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  /**
   * @Scheduled 애노테이션이 위의 taskScheduler() 를 사용하도록 설정
   */
  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    registrar.setScheduler(taskScheduler());
  }
}
