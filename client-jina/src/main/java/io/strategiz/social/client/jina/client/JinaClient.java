package io.strategiz.social.client.jina.client;

import io.github.bucket4j.Bucket;
import io.strategiz.framework.exception.CidadelException;
import io.strategiz.social.client.jina.config.JinaConfig;
import io.strategiz.social.client.jina.exception.JinaErrorDetails;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/** Client for Jina Reader API — extracts web page content as clean markdown. */
public class JinaClient {

	private static final Logger logger = LoggerFactory.getLogger(JinaClient.class);

	private static final String MODULE_NAME = "client-jina";

	private final JinaConfig config;

	private final Bucket rateLimiter;

	private final RestClient restClient;

	public JinaClient(JinaConfig config, Bucket rateLimiter) {
		this.config = config;
		this.rateLimiter = rateLimiter;
		this.restClient = RestClient.builder().build();
	}

	/** Read a web page and return its content as markdown. */
	public String readPage(String url) {
		checkRateLimit();

		try {
			RestClient.RequestHeadersSpec<?> request = restClient.get()
				.uri(URI.create(config.getBaseUrl() + "/" + url))
				.header("Accept", "text/markdown")
				.header("X-Return-Format", "markdown");

			if (config.isConfigured()) {
				request = request.header("Authorization", "Bearer " + config.getApiKey());
			}

			String content = request.retrieve().body(String.class);

			if (content == null || content.isBlank()) {
				return "No content could be extracted from " + url;
			}

			return content;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			logger.error("Jina Reader failed for URL: {}", url, e);
			throw new CidadelException(JinaErrorDetails.PAGE_READ_FAILED, MODULE_NAME, e.getMessage());
		}
	}

	private void checkRateLimit() {
		if (!rateLimiter.tryConsume(1)) {
			throw new CidadelException(JinaErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME);
		}
	}

}
