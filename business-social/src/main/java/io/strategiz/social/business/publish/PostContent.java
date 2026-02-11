package io.strategiz.social.business.publish;

import java.util.ArrayList;
import java.util.List;

public class PostContent {

	private String text;

	private List<String> mediaUrls = new ArrayList<>();

	private List<String> hashtags = new ArrayList<>();

	public PostContent() {
	}

	public PostContent(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public List<String> getMediaUrls() {
		return mediaUrls;
	}

	public void setMediaUrls(List<String> mediaUrls) {
		this.mediaUrls = mediaUrls;
	}

	public List<String> getHashtags() {
		return hashtags;
	}

	public void setHashtags(List<String> hashtags) {
		this.hashtags = hashtags;
	}

}
