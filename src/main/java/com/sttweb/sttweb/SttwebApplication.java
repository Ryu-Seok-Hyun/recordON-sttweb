package com.sttweb.sttweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SttwebApplication {

	public static void main(String[] args) {
		SpringApplication.run(SttwebApplication.class, args);
	}

}
