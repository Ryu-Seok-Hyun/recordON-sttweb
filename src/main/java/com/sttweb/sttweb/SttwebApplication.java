package com.sttweb.sttweb;

import com.sttweb.sttweb.service.TrecordScanService;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(
		scanBasePackages = "com.sttweb",
		exclude = {
				DataSourceAutoConfiguration.class,
				SecurityFilterAutoConfiguration.class,
				ErrorMvcAutoConfiguration.class
		}
)
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableRetry
@EnableSchedulerLock(defaultLockAtMostFor = "PT14M")
public class SttwebApplication extends SpringBootServletInitializer {
	private static final Logger log = LoggerFactory.getLogger(SttwebApplication.class);

	public SttwebApplication() {
		setRegisterErrorPageFilter(false);
	}

	public static void main(String[] args) {
		SpringApplication.run(SttwebApplication.class, args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		setRegisterErrorPageFilter(false);
		return builder.sources(SttwebApplication.class);
	}

	/***
	 * 애플리케이션 구동 시 초기 스캔.
	 * 예외가 나와도 앱은 계속 살아 있도록 모든 예외를 잡아 둡니다.
	 */
	@Bean
	CommandLineRunner initScan(TrecordScanService scanSvc) {
		return args -> {
			try {
				int cnt = scanSvc.scanAndSaveNewRecords();
				log.info("초기 녹취 스캔 완료, 신규 등록: {}건", cnt);
			} catch (Exception ex) {
				log.error("초기 녹취 스캔 중 오류 발생(무시하고 계속 시작):", ex);
			}
		};
	}
}
