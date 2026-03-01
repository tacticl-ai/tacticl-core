package io.tacticl.client.gcs.config;

/** Configuration for Google Cloud Storage. */
public class GcsConfig {

	private String projectId = "tacticl";

	private String profileBucket = "tacticl-browser-profiles";

	private String filesBucket = "tacticl-user-files";

	private String serviceAccountKey;

	public boolean isConfigured() {
		return serviceAccountKey != null && !serviceAccountKey.isEmpty();
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getProfileBucket() {
		return profileBucket;
	}

	public void setProfileBucket(String profileBucket) {
		this.profileBucket = profileBucket;
	}

	public String getFilesBucket() {
		return filesBucket;
	}

	public void setFilesBucket(String filesBucket) {
		this.filesBucket = filesBucket;
	}

	public String getServiceAccountKey() {
		return serviceAccountKey;
	}

	public void setServiceAccountKey(String serviceAccountKey) {
		this.serviceAccountKey = serviceAccountKey;
	}

}
