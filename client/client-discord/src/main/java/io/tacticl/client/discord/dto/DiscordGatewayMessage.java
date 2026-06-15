package io.tacticl.client.discord.dto;

/**
 * An immutable, parsed Discord Gateway {@code MESSAGE_CREATE} payload — the subset the
 * conversation bridge needs. Built by {@code DiscordGatewayClient} from the raw frame and
 * handed to a {@code DiscordGatewayListener}; everything else in the frame is dropped.
 *
 * @param id             the message snowflake (used for dedup)
 * @param channelId      the channel the message was posted in (reply + conversation-key target)
 * @param guildId        the guild snowflake, or {@code null} for a DM
 * @param authorId       the author's user snowflake
 * @param authorUsername the author's username (best-effort, for logging)
 * @param authorBot      whether the author is a bot — bridges MUST drop these (no self/loop replies)
 * @param webhookId      the webhook snowflake when the message was posted by a webhook, else null —
 *                       bridges drop these too (a webhook isn't a linked human and could loop)
 * @param content        the message text (empty unless the privileged MESSAGE CONTENT intent is on)
 */
public record DiscordGatewayMessage(String id,
                                    String channelId,
                                    String guildId,
                                    String authorId,
                                    String authorUsername,
                                    boolean authorBot,
                                    String webhookId,
                                    String content) {
}
