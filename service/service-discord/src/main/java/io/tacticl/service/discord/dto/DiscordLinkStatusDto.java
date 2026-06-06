package io.tacticl.service.discord.dto;

/**
 * Current Discord link state for the authenticated user. {@code linked=false} (with null fields)
 * means no active Discord account is bound.
 */
public record DiscordLinkStatusDto(
    boolean linked,
    String discordUserId,
    String discordUsername,
    String linkedAt
) {}
