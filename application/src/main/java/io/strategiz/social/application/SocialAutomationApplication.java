package io.strategiz.social.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.strategiz.social")
@EnableScheduling
public class SocialAutomationApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocialAutomationApplication.class, args);
	}

}
