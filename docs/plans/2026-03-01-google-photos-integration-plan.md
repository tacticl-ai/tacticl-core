# Google Photos Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Google Photos as a connected media source so the agent can search/browse a user's photo library and attach media to social posts.

**Architecture:** Reuse existing `client-google` module (shared Google OAuth config) — add `GooglePhotosClient` + DTOs there. Add `GOOGLE_PHOTOS` to `PlatformType` with a new `ConnectionCategory` field. Create `GooglePhotosProvider` in `business-social`. Create `GooglePhotosSkill` in `business-agent`. Reuse existing `exchangeGoogleToken()` for token exchange (same OAuth endpoint, different scopes).

**Tech Stack:** Google Photos Library API v1, Spring RestClient, Bucket4j rate limiting, Jackson DTOs

---

### Task 1: Add ConnectionCategory enum and GOOGLE_PHOTOS to PlatformType

**Files:**
- Create: `data-social/src/main/java/io/strategiz/social/data/entity/ConnectionCategory.java`
- Modify: `data-social/src/main/java/io/strategiz/social/data/entity/PlatformType.java`

**Step 1: Create ConnectionCategory enum**

```java
// data-social/src/main/java/io/strategiz/social/data/entity/ConnectionCategory.java
package io.strategiz.social.data.entity;

public enum ConnectionCategory {
    SOCIAL,
    MEDIA_SOURCE
}
```

**Step 2: Add category field and GOOGLE_PHOTOS to PlatformType**

Modify `PlatformType.java` — add `category` field to constructor, update all existing entries to `ConnectionCategory.SOCIAL`, add `GOOGLE_PHOTOS`:

```java
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

    public String getDisplayName() { return displayName; }
    public int getMaxCaptionLength() { return maxCaptionLength; }
    public int getMaxImages() { return maxImages; }
    public ConnectionCategory getCategory() { return category; }
}
```

**Step 3: Build to verify compilation**

Run: `./gradlew :data-social:build -x test`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add data-social/src/main/java/io/strategiz/social/data/entity/ConnectionCategory.java \
       data-social/src/main/java/io/strategiz/social/data/entity/PlatformType.java
git commit -m "feat: Add ConnectionCategory enum and GOOGLE_PHOTOS to PlatformType"
```

---

### Task 2: Add GooglePhotosClient and DTOs to client-google module

**Files:**
- Modify: `client-google/build.gradle.kts` (add jackson, bucket4j, client-base deps)
- Create: `client-google/src/main/java/io/strategiz/social/client/google/dto/Album.java`
- Create: `client-google/src/main/java/io/strategiz/social/client/google/dto/MediaItem.java`
- Create: `client-google/src/main/java/io/strategiz/social/client/google/dto/MediaMetadata.java`
- Create: `client-google/src/main/java/io/strategiz/social/client/google/dto/SearchFilters.java`
- Create: `client-google/src/main/java/io/strategiz/social/client/google/exception/GooglePhotosErrorDetails.java`
- Create: `client-google/src/main/java/io/strategiz/social/client/google/client/GooglePhotosClient.java`
- Modify: `client-google/src/main/java/io/strategiz/social/client/google/config/ClientGoogleConfig.java` (add GooglePhotosClient bean)

**Step 1: Update client-google/build.gradle.kts**

Add dependencies needed for an API client (matching client-twitter pattern):

```kotlin
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Cidadel shared infrastructure
    implementation(libs.cidadel.framework.exception)
    implementation(libs.cidadel.framework.secrets)
    implementation(libs.cidadel.client.base)

    // Spring Boot
    implementation(libs.spring.boot.starter.web)

    // HTTP & JSON
    implementation(libs.jackson.databind)

    // Rate limiting
    implementation(libs.bucket4j.core)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}
```

**Step 2: Create DTO classes**

`Album.java`:
```java
package io.strategiz.social.client.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Album {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("mediaItemsCount")
    private long mediaItemsCount;

    @JsonProperty("coverPhotoBaseUrl")
    private String coverPhotoBaseUrl;

    @JsonProperty("coverPhotoMediaItemId")
    private String coverPhotoMediaItemId;

    public Album() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getMediaItemsCount() { return mediaItemsCount; }
    public void setMediaItemsCount(long mediaItemsCount) { this.mediaItemsCount = mediaItemsCount; }
    public String getCoverPhotoBaseUrl() { return coverPhotoBaseUrl; }
    public void setCoverPhotoBaseUrl(String coverPhotoBaseUrl) { this.coverPhotoBaseUrl = coverPhotoBaseUrl; }
    public String getCoverPhotoMediaItemId() { return coverPhotoMediaItemId; }
    public void setCoverPhotoMediaItemId(String coverPhotoMediaItemId) { this.coverPhotoMediaItemId = coverPhotoMediaItemId; }
}
```

`MediaMetadata.java`:
```java
package io.strategiz.social.client.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaMetadata {

    @JsonProperty("creationTime")
    private String creationTime;

    @JsonProperty("width")
    private String width;

    @JsonProperty("height")
    private String height;

    public MediaMetadata() {}

    public String getCreationTime() { return creationTime; }
    public void setCreationTime(String creationTime) { this.creationTime = creationTime; }
    public String getWidth() { return width; }
    public void setWidth(String width) { this.width = width; }
    public String getHeight() { return height; }
    public void setHeight(String height) { this.height = height; }
}
```

`MediaItem.java`:
```java
package io.strategiz.social.client.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaItem {

    @JsonProperty("id")
    private String id;

    @JsonProperty("baseUrl")
    private String baseUrl;

    @JsonProperty("mimeType")
    private String mimeType;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("mediaMetadata")
    private MediaMetadata mediaMetadata;

    @JsonProperty("productUrl")
    private String productUrl;

    public MediaItem() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public MediaMetadata getMediaMetadata() { return mediaMetadata; }
    public void setMediaMetadata(MediaMetadata mediaMetadata) { this.mediaMetadata = mediaMetadata; }
    public String getProductUrl() { return productUrl; }
    public void setProductUrl(String productUrl) { this.productUrl = productUrl; }

    /** Get sized download URL. Append =w{width}-h{height} to baseUrl. */
    public String getSizedUrl(int width, int height) {
        return baseUrl + "=w" + width + "-h" + height;
    }

    /** Get full-resolution download URL. */
    public String getFullResolutionUrl() {
        return baseUrl + "=d";
    }
}
```

`SearchFilters.java`:
```java
package io.strategiz.social.client.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchFilters {

    @JsonProperty("dateFilter")
    private DateFilter dateFilter;

    @JsonProperty("contentFilter")
    private ContentFilter contentFilter;

    @JsonProperty("mediaTypeFilter")
    private MediaTypeFilter mediaTypeFilter;

    public SearchFilters() {}

    public DateFilter getDateFilter() { return dateFilter; }
    public void setDateFilter(DateFilter dateFilter) { this.dateFilter = dateFilter; }
    public ContentFilter getContentFilter() { return contentFilter; }
    public void setContentFilter(ContentFilter contentFilter) { this.contentFilter = contentFilter; }
    public MediaTypeFilter getMediaTypeFilter() { return mediaTypeFilter; }
    public void setMediaTypeFilter(MediaTypeFilter mediaTypeFilter) { this.mediaTypeFilter = mediaTypeFilter; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DateFilter {
        @JsonProperty("ranges")
        private List<DateRange> ranges;

        public DateFilter() {}
        public List<DateRange> getRanges() { return ranges; }
        public void setRanges(List<DateRange> ranges) { this.ranges = ranges; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DateRange {
        @JsonProperty("startDate")
        private DateObj startDate;
        @JsonProperty("endDate")
        private DateObj endDate;

        public DateRange() {}
        public DateObj getStartDate() { return startDate; }
        public void setStartDate(DateObj startDate) { this.startDate = startDate; }
        public DateObj getEndDate() { return endDate; }
        public void setEndDate(DateObj endDate) { this.endDate = endDate; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DateObj {
        @JsonProperty("year")
        private int year;
        @JsonProperty("month")
        private int month;
        @JsonProperty("day")
        private int day;

        public DateObj() {}
        public DateObj(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }
        public int getYear() { return year; }
        public void setYear(int year) { this.year = year; }
        public int getMonth() { return month; }
        public void setMonth(int month) { this.month = month; }
        public int getDay() { return day; }
        public void setDay(int day) { this.day = day; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentFilter {
        @JsonProperty("includedContentCategories")
        private List<String> includedContentCategories;

        public ContentFilter() {}
        public List<String> getIncludedContentCategories() { return includedContentCategories; }
        public void setIncludedContentCategories(List<String> cats) { this.includedContentCategories = cats; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MediaTypeFilter {
        @JsonProperty("mediaTypes")
        private List<String> mediaTypes;

        public MediaTypeFilter() {}
        public List<String> getMediaTypes() { return mediaTypes; }
        public void setMediaTypes(List<String> mediaTypes) { this.mediaTypes = mediaTypes; }
    }
}
```

**Step 3: Create GooglePhotosErrorDetails**

```java
// client-google/src/main/java/io/strategiz/social/client/google/exception/GooglePhotosErrorDetails.java
package io.strategiz.social.client.google.exception;

import io.strategiz.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

public enum GooglePhotosErrorDetails implements ErrorDetails {

    UNAUTHORIZED("GOOGLE_PHOTOS_UNAUTHORIZED", "Google Photos authentication failed", HttpStatus.UNAUTHORIZED),
    RATE_LIMIT_EXCEEDED("GOOGLE_PHOTOS_RATE_LIMIT", "Google Photos API rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    NOT_FOUND("GOOGLE_PHOTOS_NOT_FOUND", "Google Photos resource not found", HttpStatus.NOT_FOUND),
    API_ERROR("GOOGLE_PHOTOS_API_ERROR", "Google Photos API error", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    GooglePhotosErrorDetails(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
    @Override public HttpStatus getHttpStatus() { return httpStatus; }
}
```

**Step 4: Create GooglePhotosClient**

```java
// client-google/src/main/java/io/strategiz/social/client/google/client/GooglePhotosClient.java
package io.strategiz.social.client.google.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.bucket4j.Bucket;
import io.strategiz.framework.exception.StrategizException;
import io.strategiz.social.client.google.dto.Album;
import io.strategiz.social.client.google.dto.MediaItem;
import io.strategiz.social.client.google.dto.SearchFilters;
import io.strategiz.social.client.google.exception.GooglePhotosErrorDetails;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                    throw new StrategizException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME,
                            "Invalid or expired Google access token");
                })
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new StrategizException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME,
                            "Google Photos API returned status " + res.getStatusCode().value());
                })
                .body(AlbumsResponse.class);

            if (response == null) {
                return new AlbumsResponse();
            }
            log.info("Listed {} albums", response.getAlbums() != null ? response.getAlbums().size() : 0);
            return response;
        } catch (StrategizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to list albums: {}", e.getMessage(), e);
            throw new StrategizException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME, e);
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
                    throw new StrategizException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME,
                            "Invalid or expired Google access token");
                })
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new StrategizException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME,
                            "Google Photos API returned status " + res.getStatusCode().value());
                })
                .body(MediaItemsResponse.class);

            if (response == null) {
                return new MediaItemsResponse();
            }
            log.info("Found {} media items", response.getMediaItems() != null ? response.getMediaItems().size() : 0);
            return response;
        } catch (StrategizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to search media items: {}", e.getMessage(), e);
            throw new StrategizException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME, e);
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
                    throw new StrategizException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME,
                            "Invalid or expired Google access token");
                })
                .onStatus(this::isNotFound, (req, res) -> {
                    throw new StrategizException(GooglePhotosErrorDetails.NOT_FOUND, MODULE_NAME,
                            "Media item not found: " + mediaItemId);
                })
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new StrategizException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME,
                            "Google Photos API returned status " + res.getStatusCode().value());
                })
                .body(MediaItem.class);

            if (response == null) {
                throw new StrategizException(GooglePhotosErrorDetails.NOT_FOUND, MODULE_NAME,
                        "Empty response for media item: " + mediaItemId);
            }
            log.info("Fetched media item: {}", response.getFilename());
            return response;
        } catch (StrategizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch media item {}: {}", mediaItemId, e.getMessage(), e);
            throw new StrategizException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME, e);
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
                    throw new StrategizException(GooglePhotosErrorDetails.UNAUTHORIZED, MODULE_NAME,
                            "Invalid or expired Google access token");
                })
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new StrategizException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME,
                            "Google Photos API returned status " + res.getStatusCode().value());
                })
                .body(MediaItemsResponse.class);

            if (response == null) {
                return new MediaItemsResponse();
            }
            log.info("Found {} items in album {}", response.getMediaItems() != null ? response.getMediaItems().size() : 0, albumId);
            return response;
        } catch (StrategizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to list album {} media: {}", albumId, e.getMessage(), e);
            throw new StrategizException(GooglePhotosErrorDetails.API_ERROR, MODULE_NAME, e);
        }
    }

    private void consumeRateLimit() {
        if (!rateLimiter.tryConsume(1)) {
            throw new StrategizException(GooglePhotosErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME,
                    "Google Photos API rate limit exceeded");
        }
    }

    private boolean isUnauthorized(HttpStatusCode status) {
        return status.value() == HttpStatus.UNAUTHORIZED.value();
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

        public List<Album> getAlbums() { return albums != null ? albums : Collections.emptyList(); }
        public void setAlbums(List<Album> albums) { this.albums = albums; }
        public String getNextPageToken() { return nextPageToken; }
        public void setNextPageToken(String nextPageToken) { this.nextPageToken = nextPageToken; }
    }

    /** Response wrapper for media item listing/search. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaItemsResponse {
        @JsonProperty("mediaItems")
        private List<MediaItem> mediaItems;
        @JsonProperty("nextPageToken")
        private String nextPageToken;

        public List<MediaItem> getMediaItems() { return mediaItems != null ? mediaItems : Collections.emptyList(); }
        public void setMediaItems(List<MediaItem> mediaItems) { this.mediaItems = mediaItems; }
        public String getNextPageToken() { return nextPageToken; }
        public void setNextPageToken(String nextPageToken) { this.nextPageToken = nextPageToken; }
    }
}
```

**Step 5: Update ClientGoogleConfig to add GooglePhotosClient bean**

Modify `client-google/src/main/java/io/strategiz/social/client/google/config/ClientGoogleConfig.java`:

```java
package io.strategiz.social.client.google.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.strategiz.social.client.google.client.GooglePhotosClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "tacticl.google.enabled", havingValue = "true", matchIfMissing = false)
public class ClientGoogleConfig {

    @Bean
    public GoogleConfig googleConfig() {
        return new GoogleConfig();
    }

    @Bean(name = "googlePhotosRateLimiter")
    public Bucket googlePhotosRateLimiter() {
        Bandwidth limit = Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Bean(name = "googlePhotosRestClient")
    public RestClient googlePhotosRestClient() {
        return RestClient.builder()
            .baseUrl("https://photoslibrary.googleapis.com")
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Bean
    public GooglePhotosClient googlePhotosClient(RestClient googlePhotosRestClient,
            Bucket googlePhotosRateLimiter) {
        return new GooglePhotosClient(googlePhotosRestClient, googlePhotosRateLimiter);
    }
}
```

**Step 6: Build to verify compilation**

Run: `./gradlew :client-google:build -x test`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add client-google/
git commit -m "feat: Add GooglePhotosClient, DTOs, and error handling to client-google"
```

---

### Task 3: Add GOOGLE_PHOTOS case to OAuthTokenExchangeService

**Files:**
- Modify: `business-social/src/main/java/io/strategiz/social/business/publish/OAuthTokenExchangeService.java`

**Step 1: Add GOOGLE_PHOTOS to the switch expressions**

In `exchangeAndStore()` method (line 72-79), add the case:
```java
case GOOGLE_PHOTOS -> exchangeGoogleToken(code, codeVerifier, redirectUri);
```

In `refreshToken()` method (line 120-126), add the case:
```java
case GOOGLE_PHOTOS -> refreshGoogleToken(refreshToken);
```

The `exchangeGoogleToken()` and `refreshGoogleToken()` methods already exist (lines 233-275) and use the same Google OAuth endpoint — they work for any Google OAuth scope, the scopes only differ in the auth URL (which is in the provider).

**Step 2: Build to verify compilation**

Run: `./gradlew :business-social:build -x test`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add business-social/src/main/java/io/strategiz/social/business/publish/OAuthTokenExchangeService.java
git commit -m "feat: Add GOOGLE_PHOTOS case to OAuth token exchange"
```

---

### Task 4: Create GooglePhotosProvider

**Files:**
- Create: `business-social/src/main/java/io/strategiz/social/business/publish/GooglePhotosProvider.java`

**Step 1: Create the provider**

```java
package io.strategiz.social.business.publish;

import io.strategiz.social.client.google.config.GoogleConfig;
import io.strategiz.social.data.entity.PlatformType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Google Photos implementation of {@link SocialMediaProvider}. Read-only media source. */
@Component
@ConditionalOnProperty(name = "tacticl.google.enabled", havingValue = "true", matchIfMissing = false)
public class GooglePhotosProvider implements SocialMediaProvider {

    private static final Logger log = LoggerFactory.getLogger(GooglePhotosProvider.class);

    private static final String PHOTOS_SCOPES = "https://www.googleapis.com/auth/photoslibrary.readonly";

    private final GoogleConfig googleConfig;

    public GooglePhotosProvider(GoogleConfig googleConfig) {
        this.googleConfig = googleConfig;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.GOOGLE_PHOTOS;
    }

    @Override
    public String getProviderName() {
        return "Google Photos";
    }

    @Override
    public int getMaxCaptionLength() {
        return 0;
    }

    @Override
    public PostValidationResult validate(PostContent content) {
        throw new UnsupportedOperationException("Google Photos is a read-only media source — publishing not supported");
    }

    @Override
    public PublishResult publish(PostContent content, String accessToken) {
        throw new UnsupportedOperationException("Google Photos is a read-only media source — publishing not supported");
    }

    @Override
    public AuthUrl generateAuthUrl(String redirectUri, String state) {
        String codeVerifier = OAuthPkceUtils.generateCodeVerifier();
        String codeChallenge = OAuthPkceUtils.generateCodeChallenge(codeVerifier);
        String url = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&client_id=" + URLEncoder.encode(googleConfig.getClientId(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(PHOTOS_SCOPES, StandardCharsets.UTF_8)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8)
                + "&code_challenge_method=S256";
        return new AuthUrl(url, codeVerifier);
    }

    @Override
    public AuthTokens authenticate(String code, String codeVerifier, String redirectUri) {
        throw new UnsupportedOperationException("Token exchange handled by OAuthTokenExchangeService");
    }

    @Override
    public AuthTokens refreshToken(String refreshToken) {
        throw new UnsupportedOperationException("Token refresh handled by OAuthTokenExchangeService");
    }
}
```

**Step 2: Build to verify compilation**

Run: `./gradlew :business-social:build -x test`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add business-social/src/main/java/io/strategiz/social/business/publish/GooglePhotosProvider.java
git commit -m "feat: Add GooglePhotosProvider for OAuth and media source support"
```

---

### Task 5: Create GooglePhotosSkill for the agent

**Files:**
- Modify: `business-agent/build.gradle.kts` (add `client-google` dependency)
- Create: `business-agent/src/main/java/io/strategiz/social/business/agent/skill/GooglePhotosSkill.java`

**Step 1: Add client-google dependency to business-agent**

Add to `business-agent/build.gradle.kts` dependencies block:
```kotlin
implementation(project(":client-google"))
```

**Step 2: Create GooglePhotosSkill**

```java
package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strategiz.client.base.llm.model.ToolDefinition;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();
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
        action.put("description", "Action to perform: search (by date/type), list_albums, get_album_photos, get_photo (by ID)");

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

        // Get the user's Google Photos integration
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
        } catch (Exception e) {
            log.error("Google Photos skill failed for user {} action {}: {}", userId, action, e.getMessage(), e);
            return "Google Photos error: " + e.getMessage();
        }
    }

    private String executeSearch(JsonNode input, String accessToken, int pageSize) {
        SearchFilters filters = new SearchFilters();

        // Date filter
        if (input.has("date_start") || input.has("date_end")) {
            LocalDate start = input.has("date_start")
                    ? LocalDate.parse(input.get("date_start").asText())
                    : LocalDate.now().minusYears(1);
            LocalDate end = input.has("date_end")
                    ? LocalDate.parse(input.get("date_end").asText())
                    : LocalDate.now();

            SearchFilters.DateRange range = new SearchFilters.DateRange();
            range.setStartDate(new SearchFilters.DateObj(start.getYear(), start.getMonthValue(), start.getDayOfMonth()));
            range.setEndDate(new SearchFilters.DateObj(end.getYear(), end.getMonthValue(), end.getDayOfMonth()));

            SearchFilters.DateFilter dateFilter = new SearchFilters.DateFilter();
            dateFilter.setRanges(List.of(range));
            filters.setDateFilter(dateFilter);
        }

        // Media type filter
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
            sb.append(String.format("- Size: %sx%s\n", item.getMediaMetadata().getWidth(), item.getMediaMetadata().getHeight()));
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
            sb.append(String.format("\n   ID: %s\n   URL: %s\n\n", item.getId(), item.getSizedUrl(1024, 1024)));
        }
        if (nextPageToken != null) {
            sb.append("(More results available)");
        }
        return sb.toString().trim();
    }

    @Override
    public int getConfirmationTier() {
        return 0; // Read-only, no confirmation needed
    }
}
```

**Step 3: Build to verify compilation**

Run: `./gradlew :business-agent:build -x test`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add business-agent/build.gradle.kts \
       business-agent/src/main/java/io/strategiz/social/business/agent/skill/GooglePhotosSkill.java
git commit -m "feat: Add GooglePhotosSkill for agent photo browsing and search"
```

---

### Task 6: Full build and integration test

**Step 1: Run full build**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL — all modules compile with new GOOGLE_PHOTOS enum value and dependencies.

**Step 2: Run existing tests to check for regressions**

Run: `./gradlew test`
Expected: All existing tests pass. No regressions from the new enum field (existing code uses positional constructor args, now we've added a 4th arg — all enum values updated).

**Step 3: Commit any fixes if needed**

If compilation errors appear (e.g., other code referencing PlatformType constructor), fix and commit.

---

### Task 7: Add application.properties config

**Files:**
- Modify: `application/src/main/resources/application.properties` (or relevant profile files)

**Step 1: Verify Google is already enabled or add the property**

Check if `tacticl.google.enabled=true` already exists in application properties (it should, since YouTube uses it). If not, add it. No separate flag needed for Google Photos — it shares the `tacticl.google.enabled` flag.

**Step 2: Commit if changed**

```bash
git add application/src/main/resources/
git commit -m "feat: Ensure Google integration enabled for Photos support"
```
