package io.strategiz.social.client.google.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.strategiz.social.client.google.dto.Album;
import io.strategiz.social.client.google.dto.MediaItem;
import io.strategiz.social.client.google.dto.SearchFilters;
import io.strategiz.social.client.google.exception.GooglePhotosErrorDetails;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

public class GooglePhotosClient {

	private static final Logger log = LoggerFactory.getLogger(GooglePhotosClient.class);

	private static final String MODULE_NAME = "client-google-photos";

	private final RestClient restClient;

	private final Bucket rateLimiter;

	public GooglePhotosClient(RestClient restClient, Bucket rateLimiter) {
		this.restClient = restClient;
		this.rateLimiter = rateLimiter;
	}

	/** List user's albums (paginated). */
	public AlbumsResponse listAlbums(String accessToken, int pageSize, String pageToken) {
		consumeRateLimit();
		log.info("Listing Google Photos albums");

		try {
			String uri = "/v1/albums?pageSize=" + Math.min(pageSize, 50);
			if (pageToken != null && !pageToken.isBlank()) {
				uri += "&pageToken=" + pageToken;
			}

			AlbumsResponse response = restClient.get()
				.uri(uri)
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired Google access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME,
							"Google Photos API returned status " + res.getStatusCode().value());
				})
				.body(AlbumsResponse.class);

			if (response == null) {
				return new AlbumsResponse();
			}
			log.info("Listed {} albums", response.getAlbums() != null ? response.getAlbums().size() : 0);
			return response;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to list albums: {}", e.getMessage(), e);
			throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	/** Search media items with filters (date, content category, media type). */
	public MediaItemsResponse searchMediaItems(String accessToken, SearchFilters filters,
			int pageSize, String pageToken) {
		consumeRateLimit();
		log.info("Searching Google Photos media items");

		try {
			Map<String, Object> body = new HashMap<>();
			body.put("pageSize", Math.min(pageSize, 100));
			if (pageToken != null && !pageToken.isBlank()) {
				body.put("pageToken", pageToken);
			}
			if (filters != null) {
				body.put("filters", filters);
			}

			MediaItemsResponse response = restClient.post()
				.uri("/v1/mediaItems:search")
				.header("Authorization", "Bearer " + accessToken)
				.body(body)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired Google access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME,
							"Google Photos API returned status " + res.getStatusCode().value());
				})
				.body(MediaItemsResponse.class);

			if (response == null) {
				return new MediaItemsResponse();
			}
			log.info("Found {} media items",
					response.getMediaItems() != null ? response.getMediaItems().size() : 0);
			return response;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to search media items: {}", e.getMessage(), e);
			throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	/** Get a single media item by ID. */
	public MediaItem getMediaItem(String accessToken, String mediaItemId) {
		consumeRateLimit();
		log.info("Fetching Google Photos media item: {}", mediaItemId);

		try {
			MediaItem response = restClient.get()
				.uri("/v1/mediaItems/{id}", mediaItemId)
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired Google access token");
				})
				.onStatus(this::isNotFound, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.NOT_FOUND, MODULE_NAME,
							"Media item not found: " + mediaItemId);
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME,
							"Google Photos API returned status " + res.getStatusCode().value());
				})
				.body(MediaItem.class);

			if (response == null) {
				throw new CidadelException(GooglePhotosErrorDetails.NOT_FOUND, MODULE_NAME,
						"Empty response for media item: " + mediaItemId);
			}
			log.info("Fetched media item: {}", response.getFilename());
			return response;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to fetch media item {}: {}", mediaItemId, e.getMessage(), e);
			throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	/** Batch get multiple media items by ID (max 50). */
	public MediaItemsResponse batchGetMediaItems(String accessToken, List<String> mediaItemIds) {
		consumeRateLimit();
		log.info("Batch fetching {} Google Photos media items", mediaItemIds.size());

		try {
			String queryParams = mediaItemIds.stream()
				.limit(50)
				.map(id -> "mediaItemIds=" + id)
				.collect(Collectors.joining("&"));

			MediaItemsResponse response = restClient.get()
				.uri("/v1/mediaItems:batchGet?" + queryParams)
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired Google access token");
				})
				.onStatus(this::isForbidden, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.FORBIDDEN, MODULE_NAME,
							"Google Photos API access denied — check OAuth scopes");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME,
							"Google Photos API returned status " + res.getStatusCode().value());
				})
				.body(MediaItemsResponse.class);

			if (response == null) {
				return new MediaItemsResponse();
			}
			log.info("Batch fetched {} media items",
					response.getMediaItems() != null ? response.getMediaItems().size() : 0);
			return response;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to batch fetch media items: {}", e.getMessage(), e);
			throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	/** List media items in a specific album. */
	public MediaItemsResponse listAlbumMedia(String accessToken, String albumId,
			int pageSize, String pageToken) {
		consumeRateLimit();
		log.info("Listing media in album: {}", albumId);

		try {
			Map<String, Object> body = new HashMap<>();
			body.put("albumId", albumId);
			body.put("pageSize", Math.min(pageSize, 100));
			if (pageToken != null && !pageToken.isBlank()) {
				body.put("pageToken", pageToken);
			}

			MediaItemsResponse response = restClient.post()
				.uri("/v1/mediaItems:search")
				.header("Authorization", "Bearer " + accessToken)
				.body(body)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired Google access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME,
							"Google Photos API returned status " + res.getStatusCode().value());
				})
				.body(MediaItemsResponse.class);

			if (response == null) {
				return new MediaItemsResponse();
			}
			log.info("Found {} items in album {}",
					response.getMediaItems() != null ? response.getMediaItems().size() : 0, albumId);
			return response;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to list album {} media: {}", albumId, e.getMessage(), e);
			throw new CidadelException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	private void consumeRateLimit() {
		if (!rateLimiter.tryConsume(1)) {
			throw new CidadelException(GooglePhotosErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME,
					"Google Photos API rate limit exceeded");
		}
	}

	private boolean isUnauthorized(HttpStatusCode status) {
		return status.value() == HttpStatus.UNAUTHORIZED.value();
	}

	private boolean isForbidden(HttpStatusCode status) {
		return status.value() == HttpStatus.FORBIDDEN.value();
	}

	private boolean isNotFound(HttpStatusCode status) {
		return status.value() == HttpStatus.NOT_FOUND.value();
	}

	/** Response wrapper for album listing. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class AlbumsResponse {

		@JsonProperty("albums")
		private List<Album> albums;

		@JsonProperty("nextPageToken")
		private String nextPageToken;

		public List<Album> getAlbums() {
			return albums != null ? albums : Collections.emptyList();
		}

		public void setAlbums(List<Album> albums) {
			this.albums = albums;
		}

		public String getNextPageToken() {
			return nextPageToken;
		}

		public void setNextPageToken(String nextPageToken) {
			this.nextPageToken = nextPageToken;
		}

	}

	/** Response wrapper for media item listing/search. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class MediaItemsResponse {

		@JsonProperty("mediaItems")
		private List<MediaItem> mediaItems;

		@JsonProperty("nextPageToken")
		private String nextPageToken;

		public List<MediaItem> getMediaItems() {
			return mediaItems != null ? mediaItems : Collections.emptyList();
		}

		public void setMediaItems(List<MediaItem> mediaItems) {
			this.mediaItems = mediaItems;
		}

		public String getNextPageToken() {
			return nextPageToken;
		}

		public void setNextPageToken(String nextPageToken) {
			this.nextPageToken = nextPageToken;
		}

	}

}
