package com.sttweb.sttweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Security 필터 자동등록 제거
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
// ErrorPageFilter (Whitelabel error page) 자동등록 제거
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication(
		scanBasePackages = "com.sttweb",
		exclude = {
				SecurityFilterAutoConfiguration.class,
				ErrorMvcAutoConfiguration.class
		}
)
public class SttwebApplication extends SpringBootServletInitializer {

	public SttwebApplication() {
		// ErrorPageFilter 자동등록을 끕니다
		setRegisterErrorPageFilter(false);
	}

	public static void main(String[] args) {
		SpringApplication.run(SttwebApplication.class, args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		// (여기에도 한 번 더 넣어도 무방합니다)
		setRegisterErrorPageFilter(false);
		return builder.sources(SttwebApplication.class);
	}
}
