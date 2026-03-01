package io.tacticl.browser.config;

import java.util.List;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
@EnableConfigurationProperties(BrowserProperties.class)
public class BrowserConfig {

	private static final Logger log = LoggerFactory.getLogger(BrowserConfig.class);

	private Playwright playwright;

	@Bean
	public Playwright playwright() {
		log.info("Initializing Playwright runtime...");
		this.playwright = Playwright.create();
		return playwright;
	}

	@Bean
	public Browser chromiumBrowser(Playwright playwright, BrowserProperties props) {
		log.info("Launching Chromium browser (max {} contexts)", props.getMaxConcurrentContexts());
		return playwright.chromium().launch(new BrowserType.LaunchOptions()
			.setHeadless(true)
			.setArgs(List.of(
				"--no-sandbox",
				"--disable-gpu",
				"--disable-dev-shm-usage",
				"--block-new-web-contents"
			)));
	}

	@PreDestroy
	public void cleanup() {
		if (playwright != null) {
			log.info("Shutting down Playwright...");
			playwright.close();
		}
	}

}
