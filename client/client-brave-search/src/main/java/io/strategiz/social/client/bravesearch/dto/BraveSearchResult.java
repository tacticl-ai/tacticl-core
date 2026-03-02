package io.strategiz.social.client.bravesearch.dto;

/** A single web search result from Brave Search API. */
public class BraveSearchResult {

	private String title;

	private String url;

	private String description;

	private String age;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getAge() {
		return age;
	}

	public void setAge(String age) {
		this.age = age;
	}

}
