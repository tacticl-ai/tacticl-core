package io.tacticl.client.gcs.client;

import tools.jackson.databind.json.JsonMapper;
import io.tacticl.client.gcs.config.GcsConfig;
import io.tacticl.client.gcs.dto.GcsUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Client for Google Cloud Storage — browser profile and user file storage. */
public class GcsClient {

	private static final Logger logger = LoggerFactory.getLogger(GcsClient.class);

	private static final String GCS_BASE_URL = "https://storage.googleapis.com";

	private final GcsConfig config;

	private final RestClient restClient;

	public GcsClient(GcsConfig config) {
		this.config = config;
		this.restClient = RestClient.builder()
			.baseUrl(GCS_BASE_URL)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
	}

	/** Upload a file to a GCS bucket. */
	public GcsUploadResult upload(String bucket, String objectName, byte[] data, String contentType) {
		try {
			restClient.post()
				.uri("/upload/storage/v1/b/{bucket}/o?uploadType=media&name={name}", bucket, objectName)
				.header(HttpHeaders.CONTENT_TYPE, contentType)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
				.body(data)
				.retrieve()
				.toBodilessEntity();

			String gcsPath = String.format("gs://%s/%s", bucket, objectName);
			return new GcsUploadResult(gcsPath, objectName, data.length);
		}
		catch (Exception e) {
			logger.error("GCS upload failed: bucket={}, object={}", bucket, objectName, e);
			throw new RuntimeException("GCS upload failed: " + e.getMessage(), e);
		}
	}

	/** Download a file from GCS. */
	public byte[] download(String bucket, String objectName) {
		try {
			return restClient.get()
				.uri("/storage/v1/b/{bucket}/o/{object}?alt=media", bucket, objectName)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
				.retrieve()
				.body(byte[].class);
		}
		catch (Exception e) {
			logger.error("GCS download failed: bucket={}, object={}", bucket, objectName, e);
			throw new RuntimeException("GCS download failed: " + e.getMessage(), e);
		}
	}

	/** Delete a file from GCS. */
	public void delete(String bucket, String objectName) {
		try {
			restClient.delete()
				.uri("/storage/v1/b/{bucket}/o/{object}", bucket, objectName)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
				.retrieve()
				.toBodilessEntity();
		}
		catch (Exception e) {
			logger.error("GCS delete failed: bucket={}, object={}", bucket, objectName, e);
		}
	}

	public String getProfileBucket() {
		return config.getProfileBucket();
	}

	public String getFilesBucket() {
		return config.getFilesBucket();
	}

	private String getAccessToken() {
		// In Cloud Run, use the metadata server for default credentials
		// In local dev, fall back to service account key from Vault
		try {
			RestClient metadataClient = RestClient.builder()
				.baseUrl("http://metadata.google.internal")
				.build();
			String response = metadataClient.get()
				.uri("/computeMetadata/v1/instance/service-accounts/default/token")
				.header("Metadata-Flavor", "Google")
				.retrieve()
				.body(String.class);
			var mapper = new JsonMapper();
			return mapper.readTree(response).get("access_token").asText();
		}
		catch (Exception e) {
			logger.warn("Metadata server unavailable, using config key");
			return config.getServiceAccountKey();
		}
	}

}
