package io.strategiz.social.application.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Global MessageSource configuration that aggregates all module message sources. This
 * ensures that error messages from all modules are properly resolved by the
 * {@link io.cidadel.framework.exception.ErrorMessageService}.
 */
@Configuration
public class GlobalMessageSourceConfig {

	@Bean
	@Primary
	public MessageSource messageSource() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

		messageSource.setBasenames("messages/agent-errors", "messages/social-errors");

		messageSource.setDefaultEncoding("UTF-8");
		messageSource.setUseCodeAsDefaultMessage(false);
		messageSource.setFallbackToSystemLocale(false);

		return messageSource;
	}

}
