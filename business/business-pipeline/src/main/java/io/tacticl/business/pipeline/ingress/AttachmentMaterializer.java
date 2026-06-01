package io.tacticl.business.pipeline.ingress;

import java.util.List;

/**
 * SPI for persisting inbound {@link Attachment} bytes to durable object storage (MinIO) and
 * returning durable references the pipeline can consume. Declared here so the dispatcher stays
 * decoupled from any concrete storage client.
 *
 * <p>No implementation exists yet (no MinIO client is wired in this repo). Until one is provided,
 * {@code IngressDispatchService} injects this optionally and, when absent, passes the original
 * channel-native attachment references straight through (see the TODO in the dispatcher). This
 * keeps adapter normalization pure (no I/O at normalize time) while leaving a clean seam for the
 * MinIO client to slot into.
 */
public interface AttachmentMaterializer {

    /**
     * Fetch each attachment's bytes from its channel-native source and store them durably.
     *
     * @param attachments inbound attachments (by reference)
     * @return durable storage references (e.g. {@code minio://bucket/key}); order need not match input
     */
    List<String> materialize(List<Attachment> attachments);
}
