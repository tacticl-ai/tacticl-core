package io.tacticl.business.discord;

import io.tacticl.business.pipeline.ingress.Attachment;
import io.tacticl.business.pipeline.ingress.ChannelType;
import io.tacticl.business.pipeline.ingress.CheckpointDecisionPayload;
import io.tacticl.business.pipeline.ingress.IngressKind;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.business.pipeline.ingress.RunOrigin;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates a Discord interaction payload (already deserialized to a generic {@code Map}) into a
 * transport-neutral {@link IngressRequest}. This is a PURE method — no I/O, no Mongo, no HTTP. The
 * resolved {@code tacticlUserId} (which DOES require I/O) is supplied by the caller after
 * {@code DiscordIdentityResolver} runs, so normalization stays deterministic and unit-testable.
 *
 * <p>Three trigger shapes are normalized:
 * <ul>
 *   <li><b>{@code /pdlc} slash command</b> (interaction type 2, command type 1) →
 *       {@link IngressKind#EXPLICIT_TRIGGER}; the spark text is the {@code prompt} option.</li>
 *   <li><b>"Send to PDLC" message context-menu</b> (interaction type 2, command type 3) →
 *       {@link IngressKind#EXPLICIT_TRIGGER}; the spark text is the targeted message's content
 *       (pulled from {@code data.resolved.messages}).</li>
 *   <li><b>Approve / Request changes / Reject buttons</b> (interaction type 3, MESSAGE_COMPONENT) →
 *       {@link IngressKind#CHECKPOINT_DECISION}; the button {@code custom_id} encodes the verb,
 *       spark id, and checkpoint id.</li>
 * </ul>
 *
 * <p>Button {@code custom_id} convention (colon-delimited):
 * {@code "pdlc:<verb>:<sparkId>:<checkpointId>"} where verb ∈ {@code approve|changes|reject}.
 *
 * <p>The {@link RunOrigin#externalKey()} is the narrowest-first probe value the
 * {@code EntryPointResolver} matches: {@code "guildId:channelId"}. The resolver itself widens to
 * {@code guildId} and then the channel default; the adapter only needs to emit the narrowest key.
 * The {@code destinationHandle} is the channel snowflake (where run updates are posted), and the
 * {@code threadHandle} carries the originating message id so checkpoint updates can be grouped.
 */
@Component
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordInboundAdapter {

    /** Discord interaction types. */
    private static final int TYPE_APPLICATION_COMMAND = 2;
    private static final int TYPE_MESSAGE_COMPONENT = 3;

    /** Discord application-command types. */
    private static final int COMMAND_CHAT_INPUT = 1;   // slash command
    private static final int COMMAND_MESSAGE = 3;       // message context-menu

    /** Slash command name + the option carrying the prompt. */
    public static final String PDLC_COMMAND_NAME = "pdlc";
    private static final String PROMPT_OPTION = "prompt";

    /** Button custom_id prefix + verbs. */
    private static final String CUSTOM_ID_PREFIX = "pdlc";
    private static final String VERB_APPROVE = "approve";
    private static final String VERB_CHANGES = "changes";
    private static final String VERB_REJECT = "reject";

    /**
     * Normalizes a deserialized Discord interaction into an {@link IngressRequest}.
     *
     * @param interaction   the interaction payload as a generic map (post-Ed25519, post-dedup)
     * @param tacticlUserId the resolved internal user id, or {@code null} when the Discord identity
     *                      is unlinked — the dispatcher rejects null on state-changing kinds
     * @return the normalized request
     * @throws IllegalArgumentException when the interaction is not a recognized trigger shape
     */
    @SuppressWarnings("unchecked")
    public IngressRequest normalize(Map<String, Object> interaction, String tacticlUserId) {
        if (interaction == null) {
            throw new IllegalArgumentException("interaction is null");
        }
        int type = asInt(interaction.get("type"), -1);
        return switch (type) {
            case TYPE_APPLICATION_COMMAND -> normalizeCommand(interaction, tacticlUserId);
            case TYPE_MESSAGE_COMPONENT -> normalizeComponent(interaction, tacticlUserId);
            default -> throw new IllegalArgumentException("unsupported interaction type: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private IngressRequest normalizeCommand(Map<String, Object> interaction, String tacticlUserId) {
        Map<String, Object> data = asMap(interaction.get("data"));
        int commandType = asInt(data.get("type"), COMMAND_CHAT_INPUT);

        String text;
        List<Attachment> attachments;
        switch (commandType) {
            case COMMAND_CHAT_INPUT -> {
                text = extractPromptOption(data);
                attachments = List.of();
            }
            case COMMAND_MESSAGE -> {
                // "Send to PDLC" on an alert message: carry both the body and any attached
                // screenshots/logs so the fix run has the canonical alert artifact.
                Map<String, Object> message = resolveTargetedMessage(data);
                attachments = extractAttachments(message.get("attachments"));
                text = messageText(message, attachments);
            }
            default -> throw new IllegalArgumentException("unsupported command type: " + commandType);
        }

        return new IngressRequest(
            buildOrigin(interaction),
            tacticlUserId,
            IngressKind.EXPLICIT_TRIGGER,
            text,
            attachments,
            null,
            interactionId(interaction),
            null
        );
    }

    private IngressRequest normalizeComponent(Map<String, Object> interaction, String tacticlUserId) {
        Map<String, Object> data = asMap(interaction.get("data"));
        String customId = asString(data.get("custom_id"));
        CheckpointDecisionPayload decision = parseDecision(customId);

        IngressKind kind = decision.decision() == CheckpointDecision.CANCEL
            ? IngressKind.CANCEL_RUN
            : IngressKind.CHECKPOINT_DECISION;

        return new IngressRequest(
            buildOrigin(interaction),
            tacticlUserId,
            kind,
            null,
            List.of(),
            null,
            interactionId(interaction),
            decision
        );
    }

    /** Parses {@code "pdlc:<verb>:<sparkId>:<checkpointId>"} into a decision payload. */
    private CheckpointDecisionPayload parseDecision(String customId) {
        if (customId == null || customId.isBlank()) {
            throw new IllegalArgumentException("button custom_id is missing");
        }
        String[] parts = customId.split(":", 4);
        if (parts.length != 4 || !CUSTOM_ID_PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("unrecognized button custom_id: " + customId);
        }
        CheckpointDecision decision = switch (parts[1]) {
            case VERB_APPROVE -> CheckpointDecision.APPROVED;
            case VERB_CHANGES -> CheckpointDecision.REWORK;
            case VERB_REJECT -> CheckpointDecision.CANCEL;
            default -> throw new IllegalArgumentException("unknown decision verb: " + parts[1]);
        };
        String sparkId = parts[2];
        String checkpointId = parts[3];
        return new CheckpointDecisionPayload(sparkId, checkpointId, decision, null);
    }

    @SuppressWarnings("unchecked")
    private String extractPromptOption(Map<String, Object> data) {
        Object optionsObj = data.get("options");
        if (optionsObj instanceof List<?> options) {
            for (Object o : options) {
                if (o instanceof Map<?, ?> opt && PROMPT_OPTION.equals(opt.get("name"))) {
                    return asString(opt.get("value"));
                }
            }
        }
        throw new IllegalArgumentException("/pdlc command missing required '" + PROMPT_OPTION + "' option");
    }

    /**
     * For a "Send to PDLC" message context-menu, the targeted message id is in {@code data.target_id}
     * and the message itself (content + attachments) is in {@code data.resolved.messages[target_id]}.
     */
    private Map<String, Object> resolveTargetedMessage(Map<String, Object> data) {
        String targetId = asString(data.get("target_id"));
        Map<String, Object> resolved = asMap(data.get("resolved"));
        Map<String, Object> messages = asMap(resolved.get("messages"));
        return asMap(messages.get(targetId));
    }

    /**
     * The spark text for a "Send to PDLC": the message body, or a sensible default when the alert is
     * an image/log-only post (no caption). Throws only when there is neither text nor an attachment.
     */
    private String messageText(Map<String, Object> message, List<Attachment> attachments) {
        String content = asString(message.get("content"));
        if (content != null && !content.isBlank()) {
            return content;
        }
        if (!attachments.isEmpty()) {
            return "Investigate and fix the issue shown in the attached "
                + (attachments.size() == 1 ? "file." : attachments.size() + " files.");
        }
        throw new IllegalArgumentException("targeted message has no text content or attachments");
    }

    /**
     * Maps Discord attachment objects ({@code filename}, {@code content_type}, {@code url},
     * {@code id}, {@code size}) onto transport-neutral {@link Attachment} refs. No bytes are
     * fetched — the CDN URL is recorded for later materialization.
     */
    @SuppressWarnings("unchecked")
    private List<Attachment> extractAttachments(Object attachmentsObj) {
        if (!(attachmentsObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Attachment> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> raw) {
                Map<String, Object> att = (Map<String, Object>) raw;
                out.add(new Attachment(
                    asString(att.get("filename")),
                    asString(att.get("content_type")),
                    asString(att.get("url")),
                    asString(att.get("id")),
                    asLong(att.get("size"))));
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    /**
     * Builds the {@link RunOrigin}. {@code externalKey} is the narrowest-first probe
     * {@code "guildId:channelId"}; {@code destinationHandle} is the channel snowflake (output target);
     * {@code threadHandle} is the originating message id (for grouping checkpoint updates).
     */
    private RunOrigin buildOrigin(Map<String, Object> interaction) {
        String guildId = asString(interaction.get("guild_id"));
        String channelId = resolveChannelId(interaction);
        String externalKey = (guildId != null ? guildId : "")
            + ":" + (channelId != null ? channelId : "");
        String threadHandle = resolveOriginatingMessageId(interaction);
        return new RunOrigin(ChannelType.DISCORD, externalKey, channelId, threadHandle);
    }

    @SuppressWarnings("unchecked")
    private String resolveChannelId(Map<String, Object> interaction) {
        // Newer payloads carry channel.id; older ones carry channel_id at the top level.
        Map<String, Object> channel = asMap(interaction.get("channel"));
        String fromObj = asString(channel.get("id"));
        if (fromObj != null) {
            return fromObj;
        }
        return asString(interaction.get("channel_id"));
    }

    /** For MESSAGE_COMPONENT interactions, the host message id anchors run-update grouping. */
    private String resolveOriginatingMessageId(Map<String, Object> interaction) {
        Map<String, Object> message = asMap(interaction.get("message"));
        return asString(message.get("id"));
    }

    private String interactionId(Map<String, Object> interaction) {
        return asString(interaction.get("id"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int asInt(Object o, int fallback) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }
}
