package io.tacticl.data.discord.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Binds a pipeline run to the Discord channel (and originating message) it should render updates
 * into. Written by the Discord interactions controller right after an EXPLICIT_TRIGGER dispatch
 * returns its {@code PipelineRun}; read by {@code DiscordRunUpdateChannel} when a pipeline event
 * arrives for that run.
 *
 * <p>This binding exists because {@code PipelineCallbackEvent} carries only the run id — the
 * channel-specific output target ({@code RunOrigin.destinationHandle}) is known at trigger time and
 * must be persisted so the (stateless, fan-out) event channel can address the right Discord channel.
 * Mirrors how the Telegram channel resolves run → project → chat, but Discord's destination is a raw
 * channel snowflake captured from the interaction rather than a project link.
 */
@Document("discord_run_bindings")
public class DiscordRunBinding extends BaseMongoEntity {

    @Indexed(unique = true)
    private String pipelineRunId;

    /** Discord channel snowflake where run updates are posted (via the bot token). */
    private String channelId;

    /** Originating interaction/message id, used to anchor checkpoint-update grouping. Nullable. */
    private String originatingMessageId;

    /** Snowflake of the most recent run-update message posted, for in-place edits. Nullable. */
    private String lastUpdateMessageId;

    public static DiscordRunBinding create(String pipelineRunId, String channelId,
                                           String originatingMessageId) {
        var binding = new DiscordRunBinding();
        binding.pipelineRunId = pipelineRunId;
        binding.channelId = channelId;
        binding.originatingMessageId = originatingMessageId;
        return binding;
    }

    public String getPipelineRunId() { return pipelineRunId; }
    public void setPipelineRunId(String pipelineRunId) { this.pipelineRunId = pipelineRunId; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getOriginatingMessageId() { return originatingMessageId; }
    public void setOriginatingMessageId(String originatingMessageId) {
        this.originatingMessageId = originatingMessageId;
    }

    public String getLastUpdateMessageId() { return lastUpdateMessageId; }
    public void setLastUpdateMessageId(String lastUpdateMessageId) {
        this.lastUpdateMessageId = lastUpdateMessageId;
    }
}
