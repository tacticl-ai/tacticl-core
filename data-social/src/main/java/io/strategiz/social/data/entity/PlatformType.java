package io.strategiz.social.data.entity;

public enum PlatformType {

	TWITTER("Twitter/X", 280, 4), LINKEDIN("LinkedIn", 3000, 9), INSTAGRAM("Instagram", 2200, 10),
	REDDIT("Reddit", 40000, 20), TIKTOK("TikTok", 2200, 0), YOUTUBE("YouTube", 5000, 0),
	GITHUB("GitHub", 0, 0), GMAIL("Gmail", 0, 0), FACEBOOK("Facebook", 63206, 10);

	private final String displayName;

	private final int maxCaptionLength;

	private final int maxImages;

	PlatformType(String displayName, int maxCaptionLength, int maxImages) {
		this.displayName = displayName;
		this.maxCaptionLength = maxCaptionLength;
		this.maxImages = maxImages;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getMaxCaptionLength() {
		return maxCaptionLength;
	}

	public int getMaxImages() {
		return maxImages;
	}

}
