package com.example.JobApplicationIntelligencePlatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class JobApplicationIntelligencePlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobApplicationIntelligencePlatformApplication.class, args);
	}

}
