package io.tacticl.service.profile.dto;

import java.util.List;

/** Onboarding request to register a new product (name + repos + channel bindings). */
public record RegisterProductDto(String name, List<RepoSpecDto> repos, List<ChannelSpecDto> channels) {}
