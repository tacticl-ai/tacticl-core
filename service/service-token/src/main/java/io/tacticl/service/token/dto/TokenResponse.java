package io.tacticl.service.token.dto;

import io.tacticl.data.token.entity.PersonalAccessToken;
import java.time.Instant;

/**
 * Response for {@code GET /v1/tokens}. NEVER carries the plaintext secret — only a masked
 * label derived from the stored display prefix.
 */
public record TokenResponse(String id, String name, String maskedToken, Instant createdAt, Instant lastUsedAt) {

    public static TokenResponse from(PersonalAccessToken record) {
        String prefix = record.getTokenPrefix() == null ? "tac_" : record.getTokenPrefix();
        return new TokenResponse(
                record.getId(),
                record.getName(),
                prefix + "..." ,
                record.getCreatedAt(),
                record.getLastUsedAt());
    }
}
