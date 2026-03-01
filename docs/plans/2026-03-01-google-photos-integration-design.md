# Google Photos Integration Design

**Date**: 2026-03-01
**Status**: Approved

## Goal

Add Google Photos as a connected media source in Tacticl so the voice/chat agent can search and browse a user's photo library, then attach selected media to social posts.

## Approach

**Approach A — Full Provider Pattern**: Reuse the existing `SocialMediaProvider` interface, OAuth flow, `SocialIntegration` storage, and factory pattern. Add a `ConnectionCategory` enum to `PlatformType` so the mobile app can group Google Photos separately from social accounts.

## Design

### 1. PlatformType Changes

Add `GOOGLE_PHOTOS` to the enum. Introduce a `ConnectionCategory` enum (`SOCIAL`, `MEDIA_SOURCE`) as a new field on `PlatformType` so the mobile app renders Google Photos in a "Media Sources" section.

```java
public enum ConnectionCategory { SOCIAL, MEDIA_SOURCE }

public enum PlatformType {
    TWITTER("Twitter/X", 280, 4, ConnectionCategory.SOCIAL),
    // ... existing platforms ...
    GOOGLE_PHOTOS("Google Photos", 0, 0, ConnectionCategory.MEDIA_SOURCE);
}
```

### 2. Client Module: `client-google-photos/`

Standard client module structure following `client-twitter` pattern.

**Config layer:**
- `GooglePhotosConfig` — baseUrl (`https://photoslibrary.googleapis.com`), clientId, clientSecret, rateLimitPerMinute (default 60)
- `GooglePhotosVaultConfig` — loads `google-photos.client-id` and `google-photos.client-secret` from Vault
- `ClientGooglePhotosConfig` — Spring beans: config, rate limiter (Bucket4j), RestClient, GooglePhotosClient

**Client:**
- `GooglePhotosClient` — API methods:
  - `listAlbums(accessToken)` — paginated album list
  - `searchMediaItems(accessToken, filters)` — search by date range, content category, media type
  - `getMediaItem(accessToken, mediaItemId)` — single item with download URL
  - `batchGetMediaItems(accessToken, mediaItemIds)` — batch fetch up to 50 items

**DTOs:**
- `Album` — id, title, mediaItemsCount, coverPhotoBaseUrl
- `MediaItem` — id, baseUrl, mimeType, filename, mediaMetadata (width, height, creationTime, photo/video metadata)
- `SearchFilters` — dateRange, contentCategories, mediaTypes
- `MediaItemsSearchRequest` — filters, pageSize, pageToken, albumId

**Error handling:**
- `GooglePhotosErrorDetails` enum — UNAUTHORIZED, RATE_LIMIT_EXCEEDED, NOT_FOUND, FORBIDDEN, API_ERROR

**Feature flag:** `tacticl.google-photos.enabled`

### 3. GooglePhotosProvider

In `business-social/`, implements `SocialMediaProvider`:

- `getPlatformType()` → `GOOGLE_PHOTOS`
- `getProviderName()` → `"Google Photos"`
- `getMaxCaptionLength()` → `0`
- `generateAuthUrl(redirectUri, state)` → Google OAuth2 URL with scope `https://www.googleapis.com/auth/photoslibrary.readonly`
- `authenticate(code, codeVerifier, redirectUri)` → delegates to `OAuthTokenExchangeService`
- `refreshToken(refreshToken)` → delegates to `OAuthTokenExchangeService`
- `validate(content)` → throws `UnsupportedOperationException`
- `publish(content, accessToken)` → throws `UnsupportedOperationException`

### 4. OAuth Token Exchange

Add `exchangeGooglePhotosToken()` to `OAuthTokenExchangeService`:

- Token endpoint: `https://oauth2.googleapis.com/token`
- Grant type: `authorization_code`
- Parameters: code, client_id, client_secret, redirect_uri, grant_type
- No PKCE (Google uses client_secret instead)
- Refresh endpoint: same URL with `grant_type=refresh_token`

### 5. Agent Skill: `google_photos`

Registered in `ToolRegistry` as **Tier 0** (read-only, no confirmation needed).

```
Tool: google_photos
Description: Search and browse user's Google Photos library to find images and videos
Parameters:
  - action: "search" | "list_albums" | "get_album_photos" | "get_photo"
  - query: text description for search (optional, for "search")
  - date_start: ISO date, start of date range (optional)
  - date_end: ISO date, end of date range (optional)
  - album_id: album ID (for "get_album_photos")
  - media_id: media item ID (for "get_photo")
  - page_size: number of results (default 20, max 50)
```

Returns JSON with media items including `baseUrl` (append `=w{width}-h{height}` for sized URLs) that can be passed to social post creation.

### 6. Vault Secrets

```
secret/tacticl/google-photos:
  client-id: <Google Cloud Console OAuth 2.0 client ID>
  client-secret: <Google Cloud Console OAuth 2.0 client secret>
```

### 7. Google Cloud Setup (Prerequisites)

- Enable "Photos Library API" in Google Cloud Console
- Create OAuth 2.0 client credentials (Web application type)
- Add authorized redirect URIs for QA and prod
- Configure OAuth consent screen with `photoslibrary.readonly` scope

## Data Flow

```
User: "Post my sunset photo from last week to Instagram"
  → Agent receives command
  → Agent calls google_photos skill (action=search, query="sunset", date_range=last week)
  → Skill checks SocialIntegration for GOOGLE_PHOTOS token
  → GooglePhotosClient.searchMediaItems(accessToken, filters)
  → Returns matching MediaItems with baseUrls
  → Agent presents options to user
  → User confirms selection
  → Agent creates Instagram post with selected media baseUrl
```

## Module Dependencies

```
client-google-photos/  → framework-* (via client-base pattern)
business-social/       → client-google-photos/ (GooglePhotosProvider uses GooglePhotosClient)
business-agent/        → client-google-photos/ + data-social/ (skill needs client + integration lookup)
```
