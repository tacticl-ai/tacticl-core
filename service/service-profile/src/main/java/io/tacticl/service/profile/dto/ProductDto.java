package io.tacticl.service.profile.dto;

import io.tacticl.data.profile.entity.Product;
import java.time.Instant;
import java.util.List;

/** A user-registered product (repos + channel bindings) as returned to clients. */
public record ProductDto(
        String id,
        String name,
        List<String> repos,
        List<ChannelBindingDto> channels,
        Instant createdAt,
        Instant updatedAt) {

    /** A channel binding within a product. */
    public record ChannelBindingDto(String channelType, String externalKey, String label) {}

    public static ProductDto from(Product product) {
        List<ChannelBindingDto> channels = product.getChannels() == null ? List.of()
                : product.getChannels().stream()
                    .map(c -> new ChannelBindingDto(c.getChannelType(), c.getExternalKey(), c.getLabel()))
                    .toList();
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getRepos() == null ? List.of() : product.getRepos(),
                channels,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
