package io.strategiz.social.data.entity;

public enum PlatformType {

	TWITTER("Twitter/X", 280, 4, ConnectionCategory.SOCIAL),
	LINKEDIN("LinkedIn", 3000, 9, ConnectionCategory.SOCIAL),
	INSTAGRAM("Instagram", 2200, 10, ConnectionCategory.SOCIAL),
	REDDIT("Reddit", 40000, 20, ConnectionCategory.SOCIAL),
	TIKTOK("TikTok", 2200, 0, ConnectionCategory.SOCIAL),
	YOUTUBE("YouTube", 5000, 0, ConnectionCategory.SOCIAL),
	GITHUB("GitHub", 0, 0, ConnectionCategory.SOCIAL),
	GMAIL("Gmail", 0, 0, ConnectionCategory.SOCIAL),
	FACEBOOK("Facebook", 63206, 10, ConnectionCategory.SOCIAL),
	GOOGLE_PHOTOS("Google Photos", 0, 0, ConnectionCategory.MEDIA_SOURCE);

	private final String displayName;

	private final int maxCaptionLength;

	private final int maxImages;

	private final ConnectionCategory category;

	PlatformType(String displayName, int maxCaptionLength, int maxImages, ConnectionCategory category) {
		this.displayName = displayName;
		this.maxCaptionLength = maxCaptionLength;
		this.maxImages = maxImages;
		this.category = category;
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

	public ConnectionCategory getCategory() {
		return category;
	}

}
