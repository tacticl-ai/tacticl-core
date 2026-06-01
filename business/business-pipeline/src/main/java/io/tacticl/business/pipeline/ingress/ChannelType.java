package io.tacticl.business.pipeline.ingress;

/**
 * Transport surface a pipeline-ingress request arrived on. The channel is one half of the
 * {@code (channel, externalKey)} pair that the {@code EntryPointResolver} keys on, and is the
 * discriminator a {@code PipelineEventChannel} uses to decide whether to render run updates back
 * to that transport.
 *
 * <p>WEB is the in-app surface (REST/SSE). VOICE is the Jarvis voice command center. TELEGRAM and
 * DISCORD are the ChatOps transports.
 */
public enum ChannelType {
    DISCORD,
    TELEGRAM,
    WEB,
    VOICE
}
