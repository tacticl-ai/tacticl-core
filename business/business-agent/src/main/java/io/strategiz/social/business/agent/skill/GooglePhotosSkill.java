package io.strategiz.social.business.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.client.google.client.GooglePhotosClient;
import io.strategiz.social.client.google.dto.Album;
import io.strategiz.social.client.google.dto.MediaItem;
import io.strategiz.social.client.google.dto.SearchFilters;
import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to search and browse user's Google Photos library. Tier 0: auto-execute (read-only). */
@Component
public class GooglePhotosSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(GooglePhotosSkill.class);

	private static final JsonMapper MAPPER = new JsonMapper();

	private static final int DEFAULT_PAGE_SIZE = 20;

	private static final int MAX_PAGE_SIZE = 50;

	private final Optional<GooglePhotosClient> googlePhotosClient;

	private final SocialIntegrationRepository integrationRepository;

	public GooglePhotosSkill(Optional<GooglePhotosClient> googlePhotosClient,
			SocialIntegrationRepository integrationRepository) {
		this.googlePhotosClient = googlePhotosClient;
		this.integrationRepository = integrationRepository;
	}

	@Override
	public String getName() {
		return "google_photos";
	}

	@Override
	public String getDescription() {
		return "Search and browse user's Google Photos library to find images and videos for use in social posts";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode action = properties.putObject("action");
		action.put("type", "string");
		action.putArray("enum").add("search").add("list_albums").add("get_album_photos").add("get_photo");
		action.put("description",
				"Action to perform: search (by date/type), list_albums, get_album_photos, get_photo (by ID)");

		ObjectNode dateStart = properties.putObject("date_start");
		dateStart.put("type", "string");
		dateStart.put("description", "Start of date range (ISO date, e.g. 2026-02-20). For 'search' action.");

		ObjectNode dateEnd = properties.putObject("date_end");
		dateEnd.put("type", "string");
		dateEnd.put("description", "End of date range (ISO date, e.g. 2026-02-27). For 'search' action.");

		ObjectNode mediaType = properties.putObject("media_type");
		mediaType.put("type", "string");
		mediaType.putArray("enum").add("ALL_MEDIA").add("PHOTO").add("VIDEO");
		mediaType.put("description", "Filter by media type. For 'search' action. Default: ALL_MEDIA.");

		ObjectNode albumId = properties.putObject("album_id");
		albumId.put("type", "string");
		albumId.put("description", "Album ID. Required for 'get_album_photos' action.");

		ObjectNode mediaId = properties.putObject("media_id");
		mediaId.put("type", "string");
		mediaId.put("description", "Media item ID. Required for 'get_photo' action.");

		ObjectNode pageSize = properties.putObject("page_size");
		pageSize.put("type", "integer");
		pageSize.put("description", "Number of results to return (default 20, max 50).");

		schema.putArray("required").add("action");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String action = input.get("action").asText();

		if (googlePhotosClient.isEmpty()) {
			return "Google Photos is not enabled. Please ask your admin to enable it.";
		}

		Optional<SocialIntegration> integration = integrationRepository.findByUserIdAndPlatform(
				userId, PlatformType.GOOGLE_PHOTOS);
		if (integration.isEmpty() || integration.get().getAccessToken() == null) {
			return "Google Photos is not connected. Please connect your Google Photos account first "
					+ "via Settings > Connected Accounts.";
		}

		String accessToken = integration.get().getAccessToken();
		int pageSize = input.has("page_size")
				? Math.min(input.get("page_size").asInt(DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE)
				: DEFAULT_PAGE_SIZE;

		try {
			return switch (action) {
				case "search" -> executeSearch(input, accessToken, pageSize);
				case "list_albums" -> executeListAlbums(accessToken, pageSize);
				case "get_album_photos" -> executeGetAlbumPhotos(input, accessToken, pageSize);
				case "get_photo" -> executeGetPhoto(input, accessToken);
				default -> "Unknown action: " + action + ". Use: search, list_albums, get_album_photos, get_photo";
			};
		}
		catch (Exception e) {
			log.error("Google Photos skill failed for user {} action {}: {}", userId, action, e.getMessage(), e);
			return "Google Photos error: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

	private String executeSearch(JsonNode input, String accessToken, int pageSize) {
		SearchFilters filters = new SearchFilters();

		if (input.has("date_start") || input.has("date_end")) {
			LocalDate start = input.has("date_start")
					? LocalDate.parse(input.get("date_start").asText())
					: LocalDate.now().minusYears(1);
			LocalDate end = input.has("date_end")
					? LocalDate.parse(input.get("date_end").asText())
					: LocalDate.now();

			SearchFilters.DateRange range = new SearchFilters.DateRange();
			range.setStartDate(new SearchFilters.DateObj(start.getYear(), start.getMonthValue(),
					start.getDayOfMonth()));
			range.setEndDate(new SearchFilters.DateObj(end.getYear(), end.getMonthValue(),
					end.getDayOfMonth()));

			SearchFilters.DateFilter dateFilter = new SearchFilters.DateFilter();
			dateFilter.setRanges(List.of(range));
			filters.setDateFilter(dateFilter);
		}

		if (input.has("media_type") && !"ALL_MEDIA".equals(input.get("media_type").asText())) {
			SearchFilters.MediaTypeFilter mediaTypeFilter = new SearchFilters.MediaTypeFilter();
			mediaTypeFilter.setMediaTypes(List.of(input.get("media_type").asText()));
			filters.setMediaTypeFilter(mediaTypeFilter);
		}

		GooglePhotosClient.MediaItemsResponse response = googlePhotosClient.get()
				.searchMediaItems(accessToken, filters, pageSize, null);

		return formatMediaItems(response.getMediaItems(), response.getNextPageToken());
	}

	private String executeListAlbums(String accessToken, int pageSize) {
		GooglePhotosClient.AlbumsResponse response = googlePhotosClient.get()
				.listAlbums(accessToken, pageSize, null);

		List<Album> albums = response.getAlbums();
		if (albums.isEmpty()) {
			return "No albums found in your Google Photos library.";
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Found %d albums:\n\n", albums.size()));
		for (Album album : albums) {
			sb.append(String.format("- **%s** (ID: %s, %d items)\n",
					album.getTitle(), album.getId(), album.getMediaItemsCount()));
		}
		return sb.toString().trim();
	}

	private String executeGetAlbumPhotos(JsonNode input, String accessToken, int pageSize) {
		if (!input.has("album_id")) {
			return "album_id is required for get_album_photos action.";
		}
		String albumId = input.get("album_id").asText();

		GooglePhotosClient.MediaItemsResponse response = googlePhotosClient.get()
				.listAlbumMedia(accessToken, albumId, pageSize, null);

		return formatMediaItems(response.getMediaItems(), response.getNextPageToken());
	}

	private String executeGetPhoto(JsonNode input, String accessToken) {
		if (!input.has("media_id")) {
			return "media_id is required for get_photo action.";
		}
		String mediaId = input.get("media_id").asText();
		MediaItem item = googlePhotosClient.get().getMediaItem(accessToken, mediaId);

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("**%s**\n", item.getFilename()));
		sb.append(String.format("- Type: %s\n", item.getMimeType()));
		if (item.getMediaMetadata() != null) {
			sb.append(String.format("- Size: %sx%s\n",
					item.getMediaMetadata().getWidth(), item.getMediaMetadata().getHeight()));
			sb.append(String.format("- Created: %s\n", item.getMediaMetadata().getCreationTime()));
		}
		sb.append(String.format("- Full URL: %s\n", item.getFullResolutionUrl()));
		sb.append(String.format("- ID: %s\n", item.getId()));
		return sb.toString().trim();
	}

	private String formatMediaItems(List<MediaItem> items, String nextPageToken) {
		if (items.isEmpty()) {
			return "No photos found matching your criteria.";
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Found %d photos:\n\n", items.size()));
		for (int i = 0; i < items.size(); i++) {
			MediaItem item = items.get(i);
			sb.append(String.format("%d. **%s** (%s)", i + 1, item.getFilename(), item.getMimeType()));
			if (item.getMediaMetadata() != null && item.getMediaMetadata().getCreationTime() != null) {
				sb.append(String.format(" — %s", item.getMediaMetadata().getCreationTime()));
			}
			sb.append(String.format("\n   ID: %s\n   URL: %s\n\n",
					item.getId(), item.getSizedUrl(1024, 1024)));
		}
		if (nextPageToken != null) {
			sb.append("(More results available)");
		}
		return sb.toString().trim();
	}

}
