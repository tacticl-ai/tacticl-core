package io.tacticl.business.discord;

import io.tacticl.client.discord.DiscordRestClient;
import io.tacticl.client.discord.config.DiscordConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registers the Discord application commands the bot exposes, scoped to the configured guild, once
 * the application is fully started. Guild-scoped registration propagates instantly (global commands
 * take up to an hour), which is what we want for a single admin guild.
 *
 * <p>Two commands are registered via a single bulk-overwrite PUT:
 * <ul>
 *   <li><b>{@code /pdlc}</b> — CHAT_INPUT slash command with a required {@code prompt} string option
 *       (the spark request).</li>
 *   <li><b>"Send to PDLC"</b> — a MESSAGE context-menu command (type 3) that lets an admin route any
 *       existing message into the pipeline.</li>
 * </ul>
 *
 * <p>Registration failure must never bring the app down (parity with
 * {@code TelegramCommandRegistrar} / {@code TelegramWebhookRegistrar}) — it is logged and swallowed.
 * Dormant unless {@code tacticl.discord.enabled=true}; also no-ops when no command guild is set.
 */
@Component
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordCommandRegistrar {

    private static final Logger log = LoggerFactory.getLogger(DiscordCommandRegistrar.class);

    /** Discord application-command types. */
    private static final int COMMAND_CHAT_INPUT = 1;
    private static final int COMMAND_MESSAGE = 3;

    /** Discord command-option types. */
    private static final int OPTION_STRING = 3;

    private final DiscordRestClient discord;
    private final DiscordConfig config;

    public DiscordCommandRegistrar(DiscordRestClient discord, DiscordConfig config) {
        this.discord = discord;
        this.config = config;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        String guildId = config.getCommandGuildId();
        if (guildId == null || guildId.isBlank()) {
            log.info("Discord command registration skipped — no commandGuildId configured");
            return;
        }
        try {
            discord.registerGuildCommands(guildId, buildCommands());
            log.info("Discord guild commands registered for guild {}", guildId);
        } catch (Exception e) {
            // WHY: a Discord outage or token issue at boot must not brick the whole application.
            log.warn("Discord guild command registration failed for guild {} — continuing", guildId, e);
        }
    }

    private List<Map<String, Object>> buildCommands() {
        Map<String, Object> promptOption = Map.of(
            "type", OPTION_STRING,
            "name", "prompt",
            "description", "What should the pipeline build or fix?",
            "required", true
        );
        Map<String, Object> slashCommand = Map.of(
            "type", COMMAND_CHAT_INPUT,
            "name", DiscordInboundAdapter.PDLC_COMMAND_NAME,
            "description", "Trigger a PDLC pipeline run",
            "options", List.of(promptOption)
        );
        Map<String, Object> contextMenu = Map.of(
            "type", COMMAND_MESSAGE,
            "name", "Send to PDLC"
        );
        Map<String, Object> linkCommand = Map.of(
            "type", COMMAND_CHAT_INPUT,
            "name", "link",
            "description", "Link your Discord account to Tacticl"
        );
        return List.of(slashCommand, contextMenu, linkCommand);
    }
}
