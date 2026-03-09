package io.strategiz.social.client.bravesearch.client;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.json.JsonMapper;
import io.github.bucket4j.Bucket;
import io.cidadel.framework.exception.CidadelException;
import io.strategiz.social.client.bravesearch.config.BraveSearchConfig;
import io.strategiz.social.client.bravesearch.dto.BraveSearchResponse;
import io.strategiz.social.client.bravesearch.dto.BraveSearchResult;
import io.strategiz.social.client.bravesearch.exception.BraveSearchErrorDetails;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Client for Brave Search API — web search for AI agents. */
public class BraveSearchClient {

	private static final Logger logger = LoggerFactory.getLogger(BraveSearchClient.class);

	private static final String MODULE_NAME = "client-brave-search";

	private final BraveSearchConfig config;

	private final Bucket rateLimiter;

	private final RestClient restClient;

	private final JsonMapper objectMapper;

	public BraveSearchClient(BraveSearchConfig config, Bucket rateLimiter) {
		this.config = config;
		this.rateLimiter = rateLimiter;
		this.objectMapper = JsonMapper.builder()
			.disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();
		this.restClient = RestClient.builder()
			.baseUrl(config.getBaseUrl())
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
	}

	/** Search the web using Brave Search API. */
	public List<BraveSearchResult> search(String query, int count) {
		checkRateLimit();

		try {
			String responseBody = restClient.get()
				.uri("/res/v1/web/search?q={query}&count={count}", query, count)
				.header("X-Subscription-Token", config.getApiKey())
				.retrieve()
				.body(String.class);

			BraveSearchResponse response = objectMapper.readValue(responseBody, BraveSearchResponse.class);

			if (response == null || response.getWeb() == null || response.getWeb().getResults() == null) {
				return List.of();
			}

			return response.getWeb().getResults();
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			logger.error("Brave Search failed for query: {}", query, e);
			throw new CidadelException(BraveSearchErrorDetails.SEARCH_FAILED, MODULE_NAME, e.getMessage());
		}
	}

	private void checkRateLimit() {
		if (!rateLimiter.tryConsume(1)) {
			throw new CidadelException(BraveSearchErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME);
		}
	}

}
