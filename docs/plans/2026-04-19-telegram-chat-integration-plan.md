# Telegram Chat Integration — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a working Telegram bot integration for Tacticl that lets a user link their Telegram account via a one-time `/start <token>` flow, so subsequent phases can hook spark creation and checkpoint approvals on top.

**Architecture:** Four new Gradle modules (`client-telegram`, `data-telegram`, `business-telegram`, `service-telegram`) following the `client-brave-search` / `data-connections` patterns. Webhook-based (not long-polling) for Cloud Run compatibility. MongoDB persistence (project is migrating off Firestore). Bot token + webhook secret loaded from Vault at startup.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Gradle Kotlin DSL, Spring Data MongoDB, Bucket4j, Jackson 3 (`tools.jackson.*`), JUnit 6 (managed by Spring Boot BOM), cidadel shared framework (`io.cidadel.*`).

**Design reference:** `docs/plans/2026-04-19-telegram-chat-integration-design.md`

**Out of scope (later plans):** Spark creation from messages, checkpoint approval callbacks, outbound pipeline event fan-out, notification preferences UI, Slack/Email adapters.

**Exit criteria for Phase 1:**
- User hits `POST /v1/telegram/link` → receives one-time token + `t.me/<bot>?start=<token>` URL
- User taps link → Telegram opens bot → `/start <token>` → bot replies "✅ Linked as @<username>"
- `GET /v1/telegram/status` returns linked chat(s)
- `DELETE /v1/telegram/link/{chatId}` unlinks
- All three endpoints Vault-gated, HMAC-validated webhook, tests green, `./gradlew build` clean.

---

## Important Notes for the Executor

1. **Namespace**: Use `io.tacticl.*` for all new code (not `io.strategiz.*`). New clients follow `client-ai-arbiter` precedent (see `io.tacticl.client.arbiter.*`).
2. **Database**: MongoDB (not Firestore). Use `BaseMongoEntity` + `MongoRepository`. See `data/data-connections/.../entity/Device.java` for the pattern.
3. **Auth**: Controllers pull userId via `@RequestHeader("X-User-Id")` (see `DeviceController`). Webhook endpoint is public (no auth), HMAC-validated.
4. **Vault secrets**: Tacticl uses context `tacticl`. Path: `secret/tacticl/telegram` with keys `bot-token` and `webhook-secret`.
5. **Base classes**: Controllers extend `BaseController` (from `io.cidadel.service.base.controller`). HTTP clients should match the `BraveSearchClient` pattern (no required base class there; other clients may vary).
6. **Commit frequently**: One commit per task minimum. Use conventional commit style matching recent commits (`feat(telegram): ...`, `test(telegram): ...`).
7. **Run on a branch**: `feat/telegram-integration` off `main`. Do NOT merge until the full phase is reviewed.
8. **Fritz's work rule** (from user): Proper fix, never quick fix. If you hit an unfamiliar pattern, stop and ask — don't hack around it.

---

## File Structure Overview

Create these directories + files. Each module gets its own `build.gradle.kts`.

```
client/client-telegram/
  build.gradle.kts
  src/main/java/io/tacticl/client/telegram/
    TelegramBotClient.java
    config/
      TelegramConfig.java
      TelegramVaultConfig.java
      ClientTelegramConfig.java
    dto/
      Update.java
      Message.java
      Chat.java
      User.java
      CallbackQuery.java
      InlineKeyboardMarkup.java
      InlineKeyboardButton.java
      SendMessageRequest.java
      SendMessageResponse.java
      ApiResponse.java
      WebhookInfo.java
    exception/
      TelegramErrorDetails.java
  src/test/java/io/tacticl/client/telegram/
    TelegramBotClientTest.java

data/data-telegram/
  build.gradle.kts
  src/main/java/io/tacticl/data/telegram/
    entity/
      TelegramLink.java
      TelegramLinkToken.java
      NotificationPrefs.java
    repository/
      TelegramLinkRepository.java
      TelegramLinkTokenRepository.java
  src/test/java/io/tacticl/data/telegram/
    entity/
      TelegramLinkTest.java

business/business-telegram/
  build.gradle.kts
  src/main/java/io/tacticl/business/telegram/
    TelegramUserLinker.java
    TelegramDispatchService.java
    TelegramWebhookSecurity.java
    TelegramWebhookRegistrar.java
    formatter/
      TelegramMessageFormatter.java
  src/test/java/io/tacticl/business/telegram/
    TelegramUserLinkerTest.java
    TelegramDispatchServiceTest.java
    TelegramWebhookSecurityTest.java

service/service-telegram/
  build.gradle.kts
  src/main/java/io/tacticl/service/telegram/
    controller/
      TelegramWebhookController.java
      TelegramLinkController.java
    dto/
      LinkTokenResponseDto.java
      TelegramStatusDto.java
      LinkedChatDto.java
  src/test/java/io/tacticl/service/telegram/
    controller/
      TelegramWebhookControllerTest.java
      TelegramLinkControllerTest.java
```

Also modified:
- `settings.gradle.kts` — add the four new modules
- `application-api/build.gradle.kts` — add dependencies on the four modules (so Spring picks up the beans)
- `application-api/src/main/resources/application.yml` (+ profile variants) — add `tacticl.telegram.*` properties

---

## Task 1: Scaffold modules in `settings.gradle.kts`

**Files:**
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Add module includes**

Append the following to `settings.gradle.kts` (following the existing pattern):

```kotlin
include(":data:data-telegram")
include(":business:business-telegram")
include(":service:service-telegram")
include(":client:client-telegram")
```

- [ ] **Step 2: Create empty module directories with `build.gradle.kts`**

```bash
mkdir -p client/client-telegram/src/{main,test}/java/io/tacticl/client/telegram
mkdir -p data/data-telegram/src/{main,test}/java/io/tacticl/data/telegram
mkdir -p business/business-telegram/src/{main,test}/java/io/tacticl/business/telegram
mkdir -p service/service-telegram/src/{main,test}/java/io/tacticl/service/telegram
```

- [ ] **Step 3: Create minimal `build.gradle.kts` in each new module**

`client/client-telegram/build.gradle.kts` — use `client-brave-search/build.gradle.kts` as the template (HTTP + bucket4j):

```kotlin
plugins {
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.httpclient)
    implementation(libs.bucket4j.core)
}
```

`data/data-telegram/build.gradle.kts` — use `data/data-connections/build.gradle.kts` as the template. Read that file first, then mirror its deps (will include spring-data-mongodb).

`business/business-telegram/build.gradle.kts` — mirror `business/business-connections/build.gradle.kts`. Add dependencies on `:client:client-telegram` and `:data:data-telegram`.

`service/service-telegram/build.gradle.kts` — mirror `service/service-connections/build.gradle.kts`. Add dependencies on `:business:business-telegram`.

- [ ] **Step 4: Verify Gradle recognizes modules**

Run: `./gradlew projects`
Expected: All four new modules appear in the tree under `client:`, `data:`, `business:`, `service:`.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts client/client-telegram business/business-telegram \
        data/data-telegram service/service-telegram
git commit -m "chore(telegram): scaffold four new modules for Telegram integration"
```

---

## Task 2: Telegram DTOs (client layer)

**Files:**
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/dto/*.java`

DTOs are minimal Jackson 3-friendly records or POJOs mirroring Telegram Bot API payloads. See https://core.telegram.org/bots/api for field definitions.

- [ ] **Step 1: Create `Update.java`** — top-level webhook envelope

```java
package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Update(
    long update_id,
    Message message,
    CallbackQuery callback_query
) {}
```

- [ ] **Step 2: Create `Message.java`**

```java
package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
    long message_id,
    long date,
    Chat chat,
    User from,
    String text
) {}
```

- [ ] **Step 3: Create `Chat.java`, `User.java`, `CallbackQuery.java`**

```java
// Chat.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record Chat(long id, String type, String username, String first_name) {}

// User.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record User(long id, boolean is_bot, String username, String first_name) {}

// CallbackQuery.java  (used in Phase 3, include now for completeness)
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record CallbackQuery(
    String id, User from, Message message, String data
) {}
```

- [ ] **Step 4: Create `InlineKeyboardButton.java`, `InlineKeyboardMarkup.java`** (referenced now, used Phase 3)

```java
// InlineKeyboardButton.java
package io.tacticl.client.telegram.dto;
public record InlineKeyboardButton(String text, String callback_data) {}

// InlineKeyboardMarkup.java
package io.tacticl.client.telegram.dto;
import java.util.List;
public record InlineKeyboardMarkup(List<List<InlineKeyboardButton>> inline_keyboard) {}
```

- [ ] **Step 5: Create request/response DTOs**

```java
// SendMessageRequest.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageRequest(
    long chat_id,
    String text,
    String parse_mode,          // "MarkdownV2" or "HTML"
    InlineKeyboardMarkup reply_markup
) {
    public static SendMessageRequest plain(long chatId, String text) {
        return new SendMessageRequest(chatId, text, null, null);
    }
}

// ApiResponse.java — generic envelope { ok, result, description, error_code }
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiResponse<T>(boolean ok, T result, String description, Integer error_code) {}

// SendMessageResponse.java
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record SendMessageResponse(long message_id, Chat chat, long date, String text) {}

// WebhookInfo.java — used for setWebhook
package io.tacticl.client.telegram.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookInfo(String url, boolean has_custom_certificate, int pending_update_count) {}
```

- [ ] **Step 6: Compile check**

Run: `./gradlew :client:client-telegram:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add client/client-telegram/src/main/java/io/tacticl/client/telegram/dto
git commit -m "feat(telegram): add Bot API DTOs (Update, Message, InlineKeyboard, etc.)"
```

---

## Task 3: Telegram config classes

**Files:**
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/config/TelegramConfig.java`
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/config/TelegramVaultConfig.java`
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/config/ClientTelegramConfig.java`
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/exception/TelegramErrorDetails.java`

- [ ] **Step 1: Create `TelegramConfig.java`** — POJO mirroring `BraveSearchConfig`

```java
package io.tacticl.client.telegram.config;

public class TelegramConfig {
    private String botToken;
    private String webhookSecret;
    private String baseUrl = "https://api.telegram.org";
    private String webhookPath = "/v1/telegram/webhook";
    private String publicBaseUrl;  // e.g. https://tacticl-core-qa.run.app — required for setWebhook
    private String botUsername;     // e.g. "tacticl_bot" — used for t.me/<botUsername> deep link
    private int rateLimitPerMinute = 30;
    private int linkTokenTtlMinutes = 15;

    public boolean isConfigured() {
        return botToken != null && !botToken.isEmpty()
            && webhookSecret != null && !webhookSecret.isEmpty();
    }

    // ... standard getters/setters for all fields ...
}
```

- [ ] **Step 2: Create `TelegramErrorDetails.java`** — enum of error codes (mirror `BraveSearchErrorDetails`)

Use `BraveSearchErrorDetails.java` as the template. Include at minimum:
- `WEBHOOK_REGISTRATION_FAILED`
- `SEND_MESSAGE_FAILED`
- `RATE_LIMIT_EXCEEDED`
- `INVALID_WEBHOOK_SIGNATURE`
- `BOT_API_ERROR` (wraps Telegram's `description` field)

- [ ] **Step 3: Create `TelegramVaultConfig.java`**

```java
package io.tacticl.client.telegram.config;

import io.cidadel.framework.secrets.controller.SecretManager;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramVaultConfig {

    private final SecretManager secretManager;
    private final TelegramConfig telegramConfig;

    public TelegramVaultConfig(SecretManager secretManager, TelegramConfig telegramConfig) {
        this.secretManager = secretManager;
        this.telegramConfig = telegramConfig;
    }

    @PostConstruct
    public void loadFromVault() {
        String botToken = secretManager.readSecret("telegram.bot-token", null);
        String webhookSecret = secretManager.readSecret("telegram.webhook-secret", null);
        if (botToken != null) telegramConfig.setBotToken(botToken);
        if (webhookSecret != null) telegramConfig.setWebhookSecret(webhookSecret);
    }
}
```

- [ ] **Step 4: Create `ClientTelegramConfig.java`** — Spring beans

```java
package io.tacticl.client.telegram.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.tacticl.client.telegram.TelegramBotClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class ClientTelegramConfig {

    @Bean
    @ConfigurationProperties(prefix = "tacticl.telegram")
    public TelegramConfig telegramConfig() {
        return new TelegramConfig();
    }

    @Bean
    public Bucket telegramRateLimiter(TelegramConfig config) {
        Bandwidth limit = Bandwidth.classic(
            config.getRateLimitPerMinute(),
            Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    @Bean
    public TelegramBotClient telegramBotClient(TelegramConfig config, Bucket telegramRateLimiter) {
        return new TelegramBotClient(config, telegramRateLimiter);
    }
}
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew :client:client-telegram:compileJava`
Expected: FAIL — `TelegramBotClient` doesn't exist yet. That's expected; Task 4 fixes it.

- [ ] **Step 6: Commit**

```bash
git add client/client-telegram/src/main/java/io/tacticl/client/telegram/config \
        client/client-telegram/src/main/java/io/tacticl/client/telegram/exception
git commit -m "feat(telegram): add config, Vault loader, Spring beans, error details"
```

---

## Task 4: `TelegramBotClient`

**Files:**
- Create: `client/client-telegram/src/main/java/io/tacticl/client/telegram/TelegramBotClient.java`
- Create: `client/client-telegram/src/test/java/io/tacticl/client/telegram/TelegramBotClientTest.java`

The client wraps Telegram's HTTPS API at `https://api.telegram.org/bot<TOKEN>/<method>`. Use `RestClient` pattern from `BraveSearchClient`.

Methods needed for Phase 1:
- `sendMessage(SendMessageRequest)` → `SendMessageResponse`
- `setWebhook(String url, String secretToken)` → `boolean`
- `getWebhookInfo()` → `WebhookInfo`

- [ ] **Step 1: Write failing test** for `sendMessage`

```java
package io.tacticl.client.telegram;

import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramBotClientTest {

    @Test
    void sendMessage_rateLimitExceeded_throws() {
        TelegramConfig config = new TelegramConfig();
        config.setBotToken("test-token");
        Bucket bucket = mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(false);

        TelegramBotClient client = new TelegramBotClient(config, bucket);

        assertThrows(Exception.class, () ->
            client.sendMessage(SendMessageRequest.plain(123L, "hi"))
        );
    }
}
```

- [ ] **Step 2: Run test, confirm failure** (class doesn't exist)

Run: `./gradlew :client:client-telegram:test`
Expected: Compilation failure.

- [ ] **Step 3: Implement `TelegramBotClient`**

```java
package io.tacticl.client.telegram;

import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.*;
import io.tacticl.client.telegram.exception.TelegramErrorDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public class TelegramBotClient {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotClient.class);
    private static final String MODULE_NAME = "client-telegram";

    private final TelegramConfig config;
    private final Bucket rateLimiter;
    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public TelegramBotClient(TelegramConfig config, Bucket rateLimiter) {
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
        this.restClient = RestClient.builder()
            .baseUrl(config.getBaseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public SendMessageResponse sendMessage(SendMessageRequest request) {
        checkRateLimit();
        try {
            String body = restClient.post()
                .uri("/bot{token}/sendMessage", config.getBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
            ApiResponse<SendMessageResponse> response = objectMapper.readValue(
                body,
                objectMapper.getTypeFactory().constructParametricType(
                    ApiResponse.class, SendMessageResponse.class));
            if (!response.ok()) {
                throw new CidadelException(TelegramErrorDetails.BOT_API_ERROR, MODULE_NAME,
                    response.description());
            }
            return response.result();
        } catch (CidadelException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Telegram sendMessage failed", e);
            throw new CidadelException(TelegramErrorDetails.SEND_MESSAGE_FAILED, MODULE_NAME,
                e.getMessage());
        }
    }

    public boolean setWebhook(String url, String secretToken) {
        checkRateLimit();
        try {
            var payload = java.util.Map.of(
                "url", url,
                "secret_token", secretToken,
                "allowed_updates", java.util.List.of("message", "callback_query"),
                "drop_pending_updates", true
            );
            String body = restClient.post()
                .uri("/bot{token}/setWebhook", config.getBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
            ApiResponse<Boolean> response = objectMapper.readValue(
                body,
                objectMapper.getTypeFactory().constructParametricType(
                    ApiResponse.class, Boolean.class));
            return response.ok() && Boolean.TRUE.equals(response.result());
        } catch (Exception e) {
            logger.error("Telegram setWebhook failed", e);
            throw new CidadelException(TelegramErrorDetails.WEBHOOK_REGISTRATION_FAILED,
                MODULE_NAME, e.getMessage());
        }
    }

    public WebhookInfo getWebhookInfo() {
        checkRateLimit();
        try {
            String body = restClient.get()
                .uri("/bot{token}/getWebhookInfo", config.getBotToken())
                .retrieve()
                .body(String.class);
            ApiResponse<WebhookInfo> response = objectMapper.readValue(
                body,
                objectMapper.getTypeFactory().constructParametricType(
                    ApiResponse.class, WebhookInfo.class));
            return response.ok() ? response.result() : null;
        } catch (Exception e) {
            logger.error("Telegram getWebhookInfo failed", e);
            throw new CidadelException(TelegramErrorDetails.WEBHOOK_REGISTRATION_FAILED,
                MODULE_NAME, e.getMessage());
        }
    }

    private void checkRateLimit() {
        if (!rateLimiter.tryConsume(1)) {
            throw new CidadelException(TelegramErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME);
        }
    }
}
```

- [ ] **Step 4: Run the rate-limit test, confirm pass**

Run: `./gradlew :client:client-telegram:test`
Expected: PASS.

- [ ] **Step 5: Add integration-style test against a mock server** (use Spring's `MockRestServiceServer` or WireMock — match whatever the codebase already uses; check `client-brave-search/src/test/` for precedent first).

If no test dep for WireMock exists, add it to `gradle/libs.versions.toml`. Otherwise use the existing pattern. A single happy-path test for `sendMessage` returning a `SendMessageResponse` is sufficient.

- [ ] **Step 6: Commit**

```bash
git add client/client-telegram
git commit -m "feat(telegram): implement TelegramBotClient (sendMessage, setWebhook)"
```

---

## Task 5: MongoDB entities and repositories

**Files:**
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/TelegramLink.java`
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/TelegramLinkToken.java`
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/entity/NotificationPrefs.java`
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/repository/TelegramLinkRepository.java`
- Create: `data/data-telegram/src/main/java/io/tacticl/data/telegram/repository/TelegramLinkTokenRepository.java`
- Create: `data/data-telegram/src/test/java/io/tacticl/data/telegram/entity/TelegramLinkTest.java`

Pattern reference: `data/data-connections/.../entity/Device.java` and `.../repository/DeviceRepository.java`.

- [ ] **Step 1: Create `NotificationPrefs.java`** (embedded)

```java
package io.tacticl.data.telegram.entity;

public class NotificationPrefs {
    private boolean checkpoints = true;
    private boolean progress = false;
    private boolean completion = true;
    private boolean failures = true;

    // standard getters/setters
}
```

- [ ] **Step 2: Create `TelegramLink.java`**

```java
package io.tacticl.data.telegram.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("telegram_links")
@CompoundIndex(name = "user_chat_unique", def = "{'userId': 1, 'chatId': 1}", unique = true)
public class TelegramLink extends BaseMongoEntity {

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private long chatId;

    private String username;
    private String firstName;
    private Instant linkedAt;
    private NotificationPrefs notificationPrefs = new NotificationPrefs();
    private boolean isActive = true;

    public static TelegramLink create(String userId, long chatId, String username, String firstName) {
        var link = new TelegramLink();
        link.userId = userId;
        link.chatId = chatId;
        link.username = username;
        link.firstName = firstName;
        link.linkedAt = Instant.now();
        return link;
    }

    public void deactivate() { this.isActive = false; }

    // standard getters/setters
}
```

> **Note on base class location**: verify that `BaseMongoEntity` lives in `io.tacticl.data.connections.base` (as it does today in `Device.java`) or whether it's been moved to a shared package. If moved, update the import. Do NOT copy the class — share it.

- [ ] **Step 3: Create `TelegramLinkToken.java`**

```java
package io.tacticl.data.telegram.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("telegram_link_tokens")
public class TelegramLinkToken extends BaseMongoEntity {

    @Indexed(unique = true)
    private String token;

    @Indexed
    private String userId;

    private Instant createdAt;

    // TTL index: MongoDB auto-deletes when expiresAt < now
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    private Instant consumedAt;

    public static TelegramLinkToken create(String token, String userId, int ttlMinutes) {
        var t = new TelegramLinkToken();
        t.token = token;
        t.userId = userId;
        t.createdAt = Instant.now();
        t.expiresAt = t.createdAt.plusSeconds(ttlMinutes * 60L);
        return t;
    }

    public boolean isConsumed() { return consumedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public void consume() { this.consumedAt = Instant.now(); }

    // getters/setters
}
```

- [ ] **Step 4: Create repositories**

```java
// TelegramLinkRepository.java
package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.TelegramLink;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramLinkRepository extends MongoRepository<TelegramLink, String> {
    List<TelegramLink> findByUserIdAndIsActiveTrue(String userId);
    Optional<TelegramLink> findByChatId(long chatId);
    Optional<TelegramLink> findByUserIdAndChatId(String userId, long chatId);
}

// TelegramLinkTokenRepository.java
package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.TelegramLinkToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TelegramLinkTokenRepository extends MongoRepository<TelegramLinkToken, String> {
    Optional<TelegramLinkToken> findByToken(String token);
}
```

- [ ] **Step 5: Write entity test** for `TelegramLink.create` and `deactivate`

```java
package io.tacticl.data.telegram.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TelegramLinkTest {

    @Test
    void create_initializesFieldsAndDefaults() {
        TelegramLink link = TelegramLink.create("user-1", 42L, "alice", "Alice");
        assertEquals("user-1", link.getUserId());
        assertEquals(42L, link.getChatId());
        assertTrue(link.isActive());
        assertNotNull(link.getLinkedAt());
        assertNotNull(link.getNotificationPrefs());
        assertTrue(link.getNotificationPrefs().isCheckpoints());
    }

    @Test
    void deactivate_flipsActiveFlag() {
        TelegramLink link = TelegramLink.create("user-1", 42L, "alice", "Alice");
        link.deactivate();
        assertFalse(link.isActive());
    }
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :data:data-telegram:test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add data/data-telegram
git commit -m "feat(telegram): add TelegramLink, TelegramLinkToken entities + repositories"
```

---

## Task 6: Webhook HMAC validation utility

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramWebhookSecurity.java`
- Create: `business/business-telegram/src/test/java/io/tacticl/business/telegram/TelegramWebhookSecurityTest.java`

Telegram sends header `X-Telegram-Bot-Api-Secret-Token` on every webhook call. We compare constant-time to the Vault-stored secret.

- [ ] **Step 1: Write failing test**

```java
package io.tacticl.business.telegram;

import io.tacticl.client.telegram.config.TelegramConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TelegramWebhookSecurityTest {

    @Test
    void validate_matchingSecret_returnsTrue() {
        TelegramConfig cfg = new TelegramConfig();
        cfg.setWebhookSecret("expected-secret");
        TelegramWebhookSecurity security = new TelegramWebhookSecurity(cfg);
        assertTrue(security.isValidSignature("expected-secret"));
    }

    @Test
    void validate_mismatch_returnsFalse() {
        TelegramConfig cfg = new TelegramConfig();
        cfg.setWebhookSecret("expected-secret");
        TelegramWebhookSecurity security = new TelegramWebhookSecurity(cfg);
        assertFalse(security.isValidSignature("wrong"));
    }

    @Test
    void validate_null_returnsFalse() {
        TelegramConfig cfg = new TelegramConfig();
        cfg.setWebhookSecret("expected-secret");
        TelegramWebhookSecurity security = new TelegramWebhookSecurity(cfg);
        assertFalse(security.isValidSignature(null));
    }
}
```

- [ ] **Step 2: Run tests, confirm failure**

Run: `./gradlew :business:business-telegram:test`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement**

```java
package io.tacticl.business.telegram;

import io.tacticl.client.telegram.config.TelegramConfig;
import org.springframework.stereotype.Component;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Component
public class TelegramWebhookSecurity {

    private final TelegramConfig config;

    public TelegramWebhookSecurity(TelegramConfig config) {
        this.config = config;
    }

    public boolean isValidSignature(String headerValue) {
        if (headerValue == null || config.getWebhookSecret() == null) return false;
        byte[] expected = config.getWebhookSecret().getBytes(StandardCharsets.UTF_8);
        byte[] actual = headerValue.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
```

- [ ] **Step 4: Run tests, confirm pass**

Run: `./gradlew :business:business-telegram:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramWebhookSecurity.java \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/TelegramWebhookSecurityTest.java
git commit -m "feat(telegram): add webhook HMAC signature validator"
```

---

## Task 7: `TelegramUserLinker` (account linking service)

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramUserLinker.java`
- Create: `business/business-telegram/src/test/java/io/tacticl/business/telegram/TelegramUserLinkerTest.java`

Responsibilities:
- `issueLinkToken(userId)` → creates random token, stores `TelegramLinkToken`, returns `{ token, botDeepLinkUrl }`.
- `redeemToken(token, chatId, username, firstName)` → validates token (exists, not consumed, not expired), marks consumed, creates `TelegramLink`, returns `Optional<String>` userId.
- `unlink(userId, chatId)` → soft-deletes link (sets `isActive=false`).
- `linkedChats(userId)` → list of active links.

- [ ] **Step 1: Write failing tests** (cover happy path + each failure case)

```java
package io.tacticl.business.telegram;

import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.data.telegram.entity.TelegramLink;
import io.tacticl.data.telegram.entity.TelegramLinkToken;
import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import io.tacticl.data.telegram.repository.TelegramLinkTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramUserLinkerTest {

    TelegramLinkRepository linkRepo;
    TelegramLinkTokenRepository tokenRepo;
    TelegramConfig config;
    TelegramUserLinker linker;

    @BeforeEach
    void setUp() {
        linkRepo = mock(TelegramLinkRepository.class);
        tokenRepo = mock(TelegramLinkTokenRepository.class);
        config = new TelegramConfig();
        config.setBotUsername("tacticl_bot");
        config.setLinkTokenTtlMinutes(15);
        linker = new TelegramUserLinker(linkRepo, tokenRepo, config);
    }

    @Test
    void issueLinkToken_returnsTokenAndDeepLink() {
        var issued = linker.issueLinkToken("user-1");
        assertNotNull(issued.token());
        assertTrue(issued.token().length() >= 24);
        assertTrue(issued.botDeepLinkUrl().startsWith("https://t.me/tacticl_bot?start="));
        verify(tokenRepo).save(any(TelegramLinkToken.class));
    }

    @Test
    void redeemToken_validToken_createsLinkAndReturnsUserId() {
        var token = TelegramLinkToken.create("abc123", "user-1", 15);
        when(tokenRepo.findByToken("abc123")).thenReturn(Optional.of(token));
        when(linkRepo.findByChatId(42L)).thenReturn(Optional.empty());

        Optional<String> userId = linker.redeemToken("abc123", 42L, "alice", "Alice");

        assertEquals(Optional.of("user-1"), userId);
        verify(linkRepo).save(any(TelegramLink.class));
        verify(tokenRepo).save(argThat(t -> ((TelegramLinkToken) t).isConsumed()));
    }

    @Test
    void redeemToken_unknownToken_returnsEmpty() {
        when(tokenRepo.findByToken("nope")).thenReturn(Optional.empty());
        assertTrue(linker.redeemToken("nope", 42L, "x", "X").isEmpty());
    }

    @Test
    void redeemToken_consumedToken_returnsEmpty() {
        var token = TelegramLinkToken.create("abc", "user-1", 15);
        token.consume();
        when(tokenRepo.findByToken("abc")).thenReturn(Optional.of(token));
        assertTrue(linker.redeemToken("abc", 42L, "x", "X").isEmpty());
    }

    @Test
    void redeemToken_expiredToken_returnsEmpty() {
        var token = new TelegramLinkToken();
        token.setToken("abc"); token.setUserId("user-1");
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(tokenRepo.findByToken("abc")).thenReturn(Optional.of(token));
        assertTrue(linker.redeemToken("abc", 42L, "x", "X").isEmpty());
    }

    @Test
    void unlink_existingLink_deactivates() {
        var link = TelegramLink.create("user-1", 42L, "alice", "Alice");
        when(linkRepo.findByUserIdAndChatId("user-1", 42L)).thenReturn(Optional.of(link));

        linker.unlink("user-1", 42L);

        verify(linkRepo).save(argThat(l -> !((TelegramLink) l).isActive()));
    }
}
```

- [ ] **Step 2: Implement `TelegramUserLinker`**

```java
package io.tacticl.business.telegram;

import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.data.telegram.entity.TelegramLink;
import io.tacticl.data.telegram.entity.TelegramLinkToken;
import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import io.tacticl.data.telegram.repository.TelegramLinkTokenRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class TelegramUserLinker {

    public record IssuedLink(String token, String botDeepLinkUrl) {}

    private final TelegramLinkRepository linkRepo;
    private final TelegramLinkTokenRepository tokenRepo;
    private final TelegramConfig config;
    private final SecureRandom random = new SecureRandom();

    public TelegramUserLinker(
            TelegramLinkRepository linkRepo,
            TelegramLinkTokenRepository tokenRepo,
            TelegramConfig config) {
        this.linkRepo = linkRepo;
        this.tokenRepo = tokenRepo;
        this.config = config;
    }

    public IssuedLink issueLinkToken(String userId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepo.save(TelegramLinkToken.create(token, userId, config.getLinkTokenTtlMinutes()));
        String url = "https://t.me/" + config.getBotUsername() + "?start=" + token;
        return new IssuedLink(token, url);
    }

    public Optional<String> redeemToken(String token, long chatId, String username, String firstName) {
        Optional<TelegramLinkToken> stored = tokenRepo.findByToken(token);
        if (stored.isEmpty()) return Optional.empty();

        TelegramLinkToken t = stored.get();
        if (t.isConsumed() || t.isExpired()) return Optional.empty();

        // If this chat is already linked to another user, reject. Caller can prompt unlink.
        Optional<TelegramLink> existing = linkRepo.findByChatId(chatId);
        if (existing.isPresent() && !existing.get().getUserId().equals(t.getUserId())) {
            return Optional.empty();
        }

        t.consume();
        tokenRepo.save(t);

        TelegramLink link = existing.orElseGet(() ->
            TelegramLink.create(t.getUserId(), chatId, username, firstName));
        link.setActive(true);
        linkRepo.save(link);

        return Optional.of(t.getUserId());
    }

    public void unlink(String userId, long chatId) {
        linkRepo.findByUserIdAndChatId(userId, chatId).ifPresent(link -> {
            link.deactivate();
            linkRepo.save(link);
        });
    }

    public List<TelegramLink> linkedChats(String userId) {
        return linkRepo.findByUserIdAndIsActiveTrue(userId);
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :business:business-telegram:test`
Expected: PASS on all 6 cases.

- [ ] **Step 4: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramUserLinker.java \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/TelegramUserLinkerTest.java
git commit -m "feat(telegram): add TelegramUserLinker for account linking flow"
```

---

## Task 8: `TelegramDispatchService` (webhook update router — Phase 1 minimal)

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramDispatchService.java`
- Create: `business/business-telegram/src/test/java/io/tacticl/business/telegram/TelegramDispatchServiceTest.java`

For Phase 1, only handle the `/start <token>` command. Everything else returns a "not supported yet" reply. Future phases extend this.

- [ ] **Step 1: Write failing test**

```java
package io.tacticl.business.telegram;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class TelegramDispatchServiceTest {

    @Test
    void handleStart_withValidToken_repliesSuccess() {
        TelegramUserLinker linker = mock(TelegramUserLinker.class);
        TelegramBotClient bot = mock(TelegramBotClient.class);
        when(linker.redeemToken("abc", 42L, "alice", "Alice"))
            .thenReturn(Optional.of("user-1"));

        TelegramDispatchService svc = new TelegramDispatchService(linker, bot);
        Message msg = new Message(1L, 0L,
            new Chat(42L, "private", "alice", "Alice"),
            new User(42L, false, "alice", "Alice"),
            "/start abc");

        svc.handle(new Update(1L, msg, null));

        verify(bot).sendMessage(argThat(r ->
            ((SendMessageRequest) r).chat_id() == 42L
            && ((SendMessageRequest) r).text().contains("Linked")));
    }

    @Test
    void handleStart_withInvalidToken_repliesFailure() {
        TelegramUserLinker linker = mock(TelegramUserLinker.class);
        TelegramBotClient bot = mock(TelegramBotClient.class);
        when(linker.redeemToken(any(), anyLong(), any(), any()))
            .thenReturn(Optional.empty());

        TelegramDispatchService svc = new TelegramDispatchService(linker, bot);
        Message msg = new Message(1L, 0L,
            new Chat(42L, "private", "alice", "Alice"),
            new User(42L, false, "alice", "Alice"),
            "/start bad");

        svc.handle(new Update(1L, msg, null));

        verify(bot).sendMessage(argThat(r ->
            ((SendMessageRequest) r).text().toLowerCase().contains("invalid")
            || ((SendMessageRequest) r).text().toLowerCase().contains("expired")));
    }

    @Test
    void handleUnknownCommand_repliesNotSupported() {
        TelegramUserLinker linker = mock(TelegramUserLinker.class);
        TelegramBotClient bot = mock(TelegramBotClient.class);
        TelegramDispatchService svc = new TelegramDispatchService(linker, bot);
        Message msg = new Message(1L, 0L,
            new Chat(42L, "private", "alice", "Alice"),
            new User(42L, false, "alice", "Alice"),
            "hello there");
        svc.handle(new Update(1L, msg, null));
        verify(bot).sendMessage(any(SendMessageRequest.class));
    }
}
```

- [ ] **Step 2: Implement**

```java
package io.tacticl.business.telegram;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelegramDispatchService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramDispatchService.class);

    private final TelegramUserLinker linker;
    private final TelegramBotClient bot;

    public TelegramDispatchService(TelegramUserLinker linker, TelegramBotClient bot) {
        this.linker = linker;
        this.bot = bot;
    }

    public void handle(Update update) {
        if (update.message() != null) {
            handleMessage(update.message());
        } else if (update.callback_query() != null) {
            // Phase 3: checkpoint callbacks
            logger.debug("callback_query received, not handled in Phase 1");
        }
    }

    private void handleMessage(Message msg) {
        if (msg.text() == null) return;
        long chatId = msg.chat().id();
        String text = msg.text().trim();

        if (text.startsWith("/start ")) {
            String token = text.substring("/start ".length()).trim();
            var result = linker.redeemToken(token, chatId,
                msg.from().username(), msg.from().first_name());
            if (result.isPresent()) {
                bot.sendMessage(SendMessageRequest.plain(chatId,
                    "✅ Linked as @" + msg.from().username() +
                    ". Spark creation coming soon."));
            } else {
                bot.sendMessage(SendMessageRequest.plain(chatId,
                    "❌ Invalid or expired link token. Generate a new one from your Tacticl dashboard."));
            }
        } else if (text.equals("/start")) {
            bot.sendMessage(SendMessageRequest.plain(chatId,
                "Welcome to Tacticl. To link your account, tap the link " +
                "in your dashboard (Settings → Integrations → Telegram)."));
        } else {
            bot.sendMessage(SendMessageRequest.plain(chatId,
                "Commands not supported yet. Stay tuned — spark creation and " +
                "checkpoint approvals are coming in the next release."));
        }
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :business:business-telegram:test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramDispatchService.java \
        business/business-telegram/src/test/java/io/tacticl/business/telegram/TelegramDispatchServiceTest.java
git commit -m "feat(telegram): add dispatch service with /start command routing"
```

---

## Task 9: Webhook registration on startup

**Files:**
- Create: `business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramWebhookRegistrar.java`

Registers the webhook URL with Telegram once on application startup (idempotent — Telegram's `setWebhook` is safe to call repeatedly).

- [ ] **Step 1: Implement**

```java
package io.tacticl.business.telegram;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.config.TelegramConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramWebhookRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramBotClient bot;
    private final TelegramConfig config;

    public TelegramWebhookRegistrar(TelegramBotClient bot, TelegramConfig config) {
        this.bot = bot;
        this.config = config;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void registerOnStartup() {
        if (!config.isConfigured()) {
            logger.warn("Telegram bot token missing — skipping webhook registration");
            return;
        }
        if (config.getPublicBaseUrl() == null || config.getPublicBaseUrl().isBlank()) {
            logger.warn("tacticl.telegram.public-base-url not set — skipping webhook registration");
            return;
        }
        String url = config.getPublicBaseUrl() + config.getWebhookPath();
        try {
            boolean ok = bot.setWebhook(url, config.getWebhookSecret());
            if (ok) logger.info("Telegram webhook registered at {}", url);
            else    logger.error("Telegram webhook registration returned ok=false");
        } catch (Exception e) {
            logger.error("Telegram webhook registration failed", e);
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :business:business-telegram:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add business/business-telegram/src/main/java/io/tacticl/business/telegram/TelegramWebhookRegistrar.java
git commit -m "feat(telegram): register webhook on app startup"
```

---

## Task 10: `TelegramWebhookController`

**Files:**
- Create: `service/service-telegram/src/main/java/io/tacticl/service/telegram/controller/TelegramWebhookController.java`
- Create: `service/service-telegram/src/test/java/io/tacticl/service/telegram/controller/TelegramWebhookControllerTest.java`

Public endpoint, no auth, HMAC-validated via header. Returns 200 quickly (Telegram retries on 5xx).

- [ ] **Step 1: Write failing test**

```java
package io.tacticl.service.telegram.controller;

import io.tacticl.business.telegram.TelegramDispatchService;
import io.tacticl.business.telegram.TelegramWebhookSecurity;
import io.tacticl.client.telegram.dto.Update;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramWebhookControllerTest {

    @Test
    void webhook_invalidSignature_returns401() {
        var security = mock(TelegramWebhookSecurity.class);
        var dispatch = mock(TelegramDispatchService.class);
        when(security.isValidSignature("bad")).thenReturn(false);

        var controller = new TelegramWebhookController(security, dispatch);
        ResponseEntity<Void> response = controller.webhook(
            "bad", new Update(1L, null, null));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(dispatch);
    }

    @Test
    void webhook_validSignature_dispatchesAndReturns200() {
        var security = mock(TelegramWebhookSecurity.class);
        var dispatch = mock(TelegramDispatchService.class);
        when(security.isValidSignature("good")).thenReturn(true);

        var controller = new TelegramWebhookController(security, dispatch);
        Update update = new Update(1L, null, null);
        ResponseEntity<Void> response = controller.webhook("good", update);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(dispatch).handle(update);
    }

    @Test
    void webhook_dispatchThrows_stillReturns200() {
        // Telegram retries non-2xx. Log + swallow to prevent replay storms.
        var security = mock(TelegramWebhookSecurity.class);
        var dispatch = mock(TelegramDispatchService.class);
        when(security.isValidSignature(any())).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(dispatch).handle(any());

        var controller = new TelegramWebhookController(security, dispatch);
        ResponseEntity<Void> response = controller.webhook(
            "good", new Update(1L, null, null));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
```

- [ ] **Step 2: Implement**

```java
package io.tacticl.service.telegram.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.telegram.TelegramDispatchService;
import io.tacticl.business.telegram.TelegramWebhookSecurity;
import io.tacticl.client.telegram.dto.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/telegram/webhook")
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramWebhookController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(TelegramWebhookController.class);

    @Override
    protected String getModuleName() { return "telegram-webhook"; }

    private final TelegramWebhookSecurity security;
    private final TelegramDispatchService dispatch;

    public TelegramWebhookController(
            TelegramWebhookSecurity security,
            TelegramDispatchService dispatch) {
        this.security = security;
        this.dispatch = dispatch;
    }

    @PostMapping
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody Update update) {
        if (!security.isValidSignature(secret)) {
            logger.warn("Rejected Telegram webhook with invalid signature");
            return ResponseEntity.status(401).build();
        }
        try {
            dispatch.handle(update);
        } catch (Exception e) {
            logger.error("Telegram dispatch failed for update_id={}", update.update_id(), e);
            // Swallow: we don't want Telegram retries on our internal errors
        }
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :service:service-telegram:test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add service/service-telegram/src/main/java/io/tacticl/service/telegram/controller/TelegramWebhookController.java \
        service/service-telegram/src/test/java/io/tacticl/service/telegram/controller/TelegramWebhookControllerTest.java
git commit -m "feat(telegram): add webhook controller with HMAC validation"
```

---

## Task 11: `TelegramLinkController` (user-facing endpoints)

**Files:**
- Create: `service/service-telegram/src/main/java/io/tacticl/service/telegram/controller/TelegramLinkController.java`
- Create: `service/service-telegram/src/main/java/io/tacticl/service/telegram/dto/LinkTokenResponseDto.java`
- Create: `service/service-telegram/src/main/java/io/tacticl/service/telegram/dto/TelegramStatusDto.java`
- Create: `service/service-telegram/src/main/java/io/tacticl/service/telegram/dto/LinkedChatDto.java`
- Create: `service/service-telegram/src/test/java/io/tacticl/service/telegram/controller/TelegramLinkControllerTest.java`

Endpoints:
- `POST /v1/telegram/link` → `{ token, botUrl }`
- `GET /v1/telegram/status` → `{ linked: [{ chatId, username, linkedAt }, ...] }`
- `DELETE /v1/telegram/link/{chatId}` → 204

- [ ] **Step 1: Create DTOs**

```java
// LinkTokenResponseDto.java
package io.tacticl.service.telegram.dto;
public record LinkTokenResponseDto(String token, String botUrl) {}

// LinkedChatDto.java
package io.tacticl.service.telegram.dto;
public record LinkedChatDto(long chatId, String username, String linkedAt) {}

// TelegramStatusDto.java
package io.tacticl.service.telegram.dto;
import java.util.List;
public record TelegramStatusDto(List<LinkedChatDto> linked) {}
```

- [ ] **Step 2: Write failing controller test**

```java
package io.tacticl.service.telegram.controller;

import io.tacticl.business.telegram.TelegramUserLinker;
import io.tacticl.business.telegram.TelegramUserLinker.IssuedLink;
import io.tacticl.data.telegram.entity.TelegramLink;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramLinkControllerTest {

    @Test
    void issueLink_returnsTokenAndBotUrl() {
        var linker = mock(TelegramUserLinker.class);
        when(linker.issueLinkToken("user-1"))
            .thenReturn(new IssuedLink("tok123", "https://t.me/tacticl_bot?start=tok123"));

        var controller = new TelegramLinkController(linker);
        var response = controller.issueLink("user-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("tok123", response.getBody().token());
        assertTrue(response.getBody().botUrl().contains("tok123"));
    }

    @Test
    void status_returnsLinkedChats() {
        var linker = mock(TelegramUserLinker.class);
        var link = TelegramLink.create("user-1", 42L, "alice", "Alice");
        when(linker.linkedChats("user-1")).thenReturn(List.of(link));

        var controller = new TelegramLinkController(linker);
        var response = controller.status("user-1");
        assertEquals(1, response.getBody().linked().size());
        assertEquals(42L, response.getBody().linked().get(0).chatId());
    }

    @Test
    void unlink_returnsNoContent() {
        var linker = mock(TelegramUserLinker.class);
        var controller = new TelegramLinkController(linker);
        ResponseEntity<Void> response = controller.unlink("user-1", 42L);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(linker).unlink("user-1", 42L);
    }
}
```

- [ ] **Step 3: Implement controller**

```java
package io.tacticl.service.telegram.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.telegram.TelegramUserLinker;
import io.tacticl.service.telegram.dto.LinkTokenResponseDto;
import io.tacticl.service.telegram.dto.LinkedChatDto;
import io.tacticl.service.telegram.dto.TelegramStatusDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/telegram")
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramLinkController extends BaseController {

    @Override
    protected String getModuleName() { return "telegram-link"; }

    private final TelegramUserLinker linker;

    public TelegramLinkController(TelegramUserLinker linker) {
        this.linker = linker;
    }

    @PostMapping("/link")
    public ResponseEntity<LinkTokenResponseDto> issueLink(
            @RequestHeader("X-User-Id") String userId) {
        var issued = linker.issueLinkToken(userId);
        return ResponseEntity.ok(new LinkTokenResponseDto(issued.token(), issued.botDeepLinkUrl()));
    }

    @GetMapping("/status")
    public ResponseEntity<TelegramStatusDto> status(
            @RequestHeader("X-User-Id") String userId) {
        List<LinkedChatDto> linked = linker.linkedChats(userId).stream()
            .map(l -> new LinkedChatDto(l.getChatId(), l.getUsername(),
                l.getLinkedAt() != null ? l.getLinkedAt().toString() : null))
            .toList();
        return ResponseEntity.ok(new TelegramStatusDto(linked));
    }

    @DeleteMapping("/link/{chatId}")
    public ResponseEntity<Void> unlink(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable long chatId) {
        linker.unlink(userId, chatId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :service:service-telegram:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add service/service-telegram/src/main/java/io/tacticl/service/telegram/controller/TelegramLinkController.java \
        service/service-telegram/src/main/java/io/tacticl/service/telegram/dto \
        service/service-telegram/src/test/java/io/tacticl/service/telegram/controller/TelegramLinkControllerTest.java
git commit -m "feat(telegram): add link/status/unlink REST endpoints"
```

---

## Task 12: Wire modules into application-api + config

**Files:**
- Modify: `application-api/build.gradle.kts` — add module deps
- Modify: `application-api/src/main/resources/application.yml`
- Modify: `application-api/src/main/resources/application-qa.yml`
- Modify: `application-api/src/main/resources/application-prod.yml`

- [ ] **Step 1: Read current `application-api/build.gradle.kts`** to understand dep structure, then add:

```kotlin
implementation(project(":client:client-telegram"))
implementation(project(":data:data-telegram"))
implementation(project(":business:business-telegram"))
implementation(project(":service:service-telegram"))
```

- [ ] **Step 2: Add base config** to `application-api/src/main/resources/application.yml`

```yaml
tacticl:
  telegram:
    enabled: false
    base-url: https://api.telegram.org
    webhook-path: /v1/telegram/webhook
    rate-limit-per-minute: 30
    link-token-ttl-minutes: 15
    bot-username: ""            # set per environment
    public-base-url: ""         # set per environment
```

- [ ] **Step 3: Enable in QA**

`application-qa.yml`:

```yaml
tacticl:
  telegram:
    enabled: true
    bot-username: tacticl_qa_bot
    public-base-url: https://tacticl-core-qa-<hash>.run.app
```

- [ ] **Step 4: Leave prod disabled** for now — enable after QA verification.

`application-prod.yml`:

```yaml
tacticl:
  telegram:
    enabled: false  # enable after QA verification
```

- [ ] **Step 5: Full build**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL. Fix any wiring issues.

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add application-api/build.gradle.kts application-api/src/main/resources/application*.yml
git commit -m "feat(telegram): wire telegram modules into application-api + QA config"
```

---

## Task 13: Vault provisioning (manual, document only)

This task is operational — no code changes. Document the one-time setup in the commit message.

- [ ] **Step 1: BotFather setup** (Gabriel to do)

```
Telegram → @BotFather → /newbot
  name: Tacticl QA
  username: tacticl_qa_bot
→ token from BotFather
→ /setdescription, /setcommands (can be deferred)
```

- [ ] **Step 2: Generate webhook secret**

```bash
openssl rand -hex 32
```

- [ ] **Step 3: Store in Vault** (QA environment)

```bash
vault kv put secret/tacticl/telegram \
  bot-token="<from-botfather>" \
  webhook-secret="<32-byte-hex>"
```

- [ ] **Step 4: Commit ops runbook**

Create `deployment/runbooks/telegram-setup.md` with the steps above so the next environment is a copy-paste.

```bash
git add deployment/runbooks/telegram-setup.md
git commit -m "docs(telegram): add Vault + BotFather setup runbook"
```

---

## Task 14: End-to-end smoke test (manual)

After deploying to QA, verify the happy path.

- [ ] **Step 1: Deploy to QA**

```bash
gcloud builds submit --config deployment/cloudbuild/cloudbuild-qa.yaml .
```

- [ ] **Step 2: Check webhook registration on startup**

```bash
gcloud logging read 'resource.type=cloud_run_revision AND
  resource.labels.service_name=tacticl-core-qa AND
  textPayload:"Telegram webhook registered"' --limit=5
```

Expected: One log entry `Telegram webhook registered at https://...`.

- [ ] **Step 3: Verify with Telegram's `getWebhookInfo` directly**

```bash
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

Expected: `"url": "https://tacticl-core-qa-...run.app/v1/telegram/webhook"`, `"has_custom_certificate": false`, `"pending_update_count": 0`.

- [ ] **Step 4: Issue a link token as a real QA user**

```bash
curl -X POST https://tacticl-core-qa-<hash>.run.app/v1/telegram/link \
  -H "X-User-Id: <real-qa-user-id>"
```

Expected: `{ "token": "...", "botUrl": "https://t.me/tacticl_qa_bot?start=..." }`.

- [ ] **Step 5: Open the bot URL on a phone, tap Start**

Expected: Bot replies `✅ Linked as @<username>. Spark creation coming soon.`

- [ ] **Step 6: Verify MongoDB state**

```
telegram_links → new doc with userId, chatId, isActive=true
telegram_link_tokens → token has consumedAt set
```

- [ ] **Step 7: Check `/status` endpoint**

```bash
curl https://tacticl-core-qa-<hash>.run.app/v1/telegram/status \
  -H "X-User-Id: <real-qa-user-id>"
```

Expected: `{ "linked": [{ "chatId": ..., "username": "...", "linkedAt": "..." }] }`.

- [ ] **Step 8: Test invalid signature** (HMAC rejection)

```bash
curl -X POST https://tacticl-core-qa-<hash>.run.app/v1/telegram/webhook \
  -H "Content-Type: application/json" \
  -H "X-Telegram-Bot-Api-Secret-Token: wrong-secret" \
  -d '{"update_id": 1}'
```

Expected: 401.

- [ ] **Step 9: Test unlink**

```bash
curl -X DELETE https://tacticl-core-qa-<hash>.run.app/v1/telegram/link/<chatId> \
  -H "X-User-Id: <real-qa-user-id>"
```

Expected: 204. MongoDB: `isActive=false` on the link.

- [ ] **Step 10: Write up smoke test results** in PR description. Move to Phase 2 planning.

---

## Risks & Mitigations (tactical)

| Risk | Mitigation |
|---|---|
| `setWebhook` called on every pod boot during rolling deploys — rate-limited by Telegram | `drop_pending_updates: true` + idempotent call; low QPS by design |
| `BaseMongoEntity` package drift | Executor must verify import path in `Device.java` before copying |
| Chat ID collision across Tacticl users (multiple users linking same TG account) | `findByChatId` guard in `redeemToken` rejects cross-user links |
| Bot token leak | Store only in Vault; never in repo; webhook-secret rotation documented in runbook |
| User DMs bot without `/start` | Handled — dispatch sends generic welcome |
| Telegram dropped webhook (network issue) | `TelegramWebhookRegistrar` re-runs on every ContextRefreshedEvent (= pod restart); can add scheduled re-registration if we see drift |

---

## Phase 2 (next plan, preview)

After Phase 1 lands:
1. `POST /v1/telegram/link` from web dashboard UI (tacticl-web) — generates link + QR.
2. `TelegramDispatchService` extended: plain text → `SparkService.createSpark(userId, text, source="telegram")` → reply with sparkId + dashboard URL.
3. `/sparks` command → list user's active sparks.
4. `/cancel <sparkId>` command.
5. Update tacticl-docs ecosystem diagram.

## Phase 3 (outbound events, preview)

1. `TelegramEventSubscriber` listens to `PipelineEventEmitter`.
2. Checkpoint events → `SendMessageRequest` with `InlineKeyboardMarkup`.
3. `callback_query` handler → `CheckpointService.resolve`.
4. Notification prefs endpoint.

---

## Final Exit Check

Before handing off for review:

- [ ] `./gradlew build` passes on fresh clone
- [ ] All new tests pass
- [ ] No changes to `io.strategiz.*` namespaces (new code only in `io.tacticl.*`)
- [ ] Feature flag off in prod, on in QA
- [ ] Vault secrets set in QA only
- [ ] Smoke test (Task 14) passed
- [ ] PR description links to design doc
- [ ] No hardcoded tokens or secrets in code or tests
