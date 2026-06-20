package io.tacticl.service.token.dto;

import io.tacticl.data.token.entity.PersonalAccessToken;
import java.time.Instant;

/**
 * Response for {@code POST /v1/tokens}. The {@code token} field is the plaintext secret,
 * returned <em>only</em> here at creation and never again.
 */
public record CreatedTokenResponse(String id, String name, String token, Instant createdAt) {

    public static CreatedTokenResponse from(PersonalAccessToken record, String plaintext) {
        return new CreatedTokenResponse(
                record.getId(),
                record.getName(),
                plaintext,
                record.getCreatedAt());
    }
}
