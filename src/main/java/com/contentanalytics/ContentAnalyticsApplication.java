package com.contentanalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableJpaAuditing
public class ContentAnalyticsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContentAnalyticsApplication.class, args);
	}

}