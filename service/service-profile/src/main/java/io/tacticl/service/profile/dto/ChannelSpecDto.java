package io.tacticl.service.profile.dto;

/** A channel binding (Discord/Telegram/WEB/VOICE) to register for a product during onboarding. */
public record ChannelSpecDto(String channelType, String externalKey, String label) {}
