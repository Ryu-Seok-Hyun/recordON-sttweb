package com.sttweb.sttweb;

import com.sttweb.sttweb.service.TrecordScanService;
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
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
@SpringBootApplication(
		scanBasePackages = "com.sttweb",
		exclude = {
				DataSourceAutoConfiguration.class,
				SecurityFilterAutoConfiguration.class,
				ErrorMvcAutoConfiguration.class
		}
)
public class SttwebApplication extends SpringBootServletInitializer {

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

	@Bean
	CommandLineRunner initScan(TrecordScanService scanSvc) {
		return args -> scanSvc.scanAndSaveNewRecords();
	}
}
