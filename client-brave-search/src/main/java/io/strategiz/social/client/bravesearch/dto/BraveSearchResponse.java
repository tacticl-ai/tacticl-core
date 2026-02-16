package io.strategiz.social.client.bravesearch.dto;

import java.util.List;

/** Response wrapper for Brave Search API web results. */
public class BraveSearchResponse {

	private WebResults web;

	public WebResults getWeb() {
		return web;
	}

	public void setWeb(WebResults web) {
		this.web = web;
	}

	/** Container for the web results array. */
	public static class WebResults {

		private List<BraveSearchResult> results;

		public List<BraveSearchResult> getResults() {
			return results;
		}

		public void setResults(List<BraveSearchResult> results) {
			this.results = results;
		}

	}

}
