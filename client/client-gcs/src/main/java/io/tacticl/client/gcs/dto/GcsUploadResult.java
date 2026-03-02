package io.tacticl.client.gcs.dto;

/** Immutable result of a GCS upload operation. */
public class GcsUploadResult {

	private final String gcsPath;

	private final String objectName;

	private final long sizeBytes;

	public GcsUploadResult(String gcsPath, String objectName, long sizeBytes) {
		this.gcsPath = gcsPath;
		this.objectName = objectName;
		this.sizeBytes = sizeBytes;
	}

	public String getGcsPath() {
		return gcsPath;
	}

	public String getObjectName() {
		return objectName;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

}
