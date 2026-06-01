package io.tacticl.business.pipeline.ingress;

/**
 * A transport-agnostic reference to an inbound binary an ingress request carried (a screenshot
 * dropped into a Discord message, a Telegram photo, …). The adapter that builds the
 * {@link IngressRequest} keeps normalization pure — it records the channel-native source location
 * here and does NOT fetch bytes. Materialization to durable object storage (MinIO) happens later,
 * inside {@code IngressDispatchService}, so the adapter stays free of I/O.
 *
 * <ul>
 *   <li>{@code filename} — original/display file name, may be null.</li>
 *   <li>{@code contentType} — MIME type if the transport supplied one, else null.</li>
 *   <li>{@code sourceUrl} — channel-native URL the bytes can be fetched from (e.g. a Discord CDN
 *       attachment URL). Mutually-useful with {@code sourceRef}; at least one should be present.</li>
 *   <li>{@code sourceRef} — channel-native opaque id (e.g. a Telegram {@code file_id}) for transports
 *       that gate downloads behind an API call rather than a direct URL. Nullable.</li>
 *   <li>{@code sizeBytes} — declared size if known, else {@code 0}.</li>
 * </ul>
 */
public record Attachment(
    String filename,
    String contentType,
    String sourceUrl,
    String sourceRef,
    long sizeBytes
) {}
