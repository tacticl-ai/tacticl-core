package io.tacticl.business.pipeline.ingress;

/**
 * Where an ingress request came from and where run updates should be rendered back to.
 *
 * <ul>
 *   <li>{@code channel} — the transport surface (Discord, Telegram, …).</li>
 *   <li>{@code externalKey} — the channel-scoped routing key the {@code EntryPointResolver}
 *       matches against. For Discord this is the narrowest-first probe value
 *       (e.g. {@code "guildId:channelId"} then {@code "guildId"}).</li>
 *   <li>{@code destinationHandle} — the channel-native target a {@code PipelineEventChannel}
 *       posts run updates to (e.g. a Discord channel snowflake). Distinct from
 *       {@code externalKey}: the key resolves an EntryPoint, the handle addresses output.</li>
 *   <li>{@code threadHandle} — optional channel-native thread/reply anchor for keeping a run's
 *       updates grouped (e.g. a Discord thread id or Telegram message-thread id). Nullable.</li>
 * </ul>
 */
public record RunOrigin(
    ChannelType channel,
    String externalKey,
    String destinationHandle,
    String threadHandle
) {}
