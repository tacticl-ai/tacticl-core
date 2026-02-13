package io.strategiz.social.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = { "io.strategiz.social", "io.strategiz.framework.exception" })
@EnableScheduling
public class TacticlApplication {

	public static void main(String[] args) {
		SpringApplication.run(TacticlApplication.class, args);
	}

}
