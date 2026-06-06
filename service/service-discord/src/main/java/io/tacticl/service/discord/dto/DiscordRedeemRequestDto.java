package io.tacticl.service.discord.dto;

/**
 * Body of {@code POST /v1/discord/link/redeem}: the one-time code the user obtained by running
 * {@code /link} in Discord. Redeeming binds their Discord snowflake to the authenticated account.
 */
public record DiscordRedeemRequestDto(String token) {}
