package io.tacticl.business.telegram.event;

import io.tacticl.business.agent.transcription.TranscriptionService;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.spark.TelegramSparkInitiator;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.TelegramFile;
import io.tacticl.client.telegram.dto.Voice;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapter that turns a Telegram voice message into a Spark by downloading the audio,
 * transcribing it, and forwarding the transcript to {@link TelegramSparkInitiator} as
 * if it had been an implicit {@code /spark} text invocation.
 *
 * <p>Wired into {@code TelegramDispatchService} as an optional collaborator: when the
 * Whisper transcription bean is absent (e.g. dev profile with {@code tacticl.whisper.enabled=false})
 * the dispatcher falls back to its silent debug-log drop and this handler is never instantiated.
 */
@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class VoiceMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceMessageHandler.class);

    private final TelegramBotClient bot;
    private final TranscriptionService transcription;
    private final TelegramIdentityResolver identity;
    private final TelegramProjectLinkRepository projects;
    private final TelegramSparkInitiator initiator;
    private final TelegramOutboundQueue outbound;

    public VoiceMessageHandler(TelegramBotClient bot,
                               TranscriptionService transcription,
                               TelegramIdentityResolver identity,
                               TelegramProjectLinkRepository projects,
                               TelegramSparkInitiator initiator,
                               TelegramOutboundQueue outbound) {
        this.bot = bot;
        this.transcription = transcription;
        this.identity = identity;
        this.projects = projects;
        this.initiator = initiator;
        this.outbound = outbound;
    }

    public void handle(Message msg) {
        if (msg == null || msg.voice() == null || msg.chat() == null) {
            return;
        }
        long chatId = msg.chat().id();
        long fromId = msg.from() != null ? msg.from().id() : 0L;

        Optional<String> userId = identity.resolveByChatId(fromId);
        if (userId.isEmpty()) {
            reply(chatId, "You must link your Tacticl account first.");
            return;
        }
        Optional<TelegramProjectLink> link = projects.findByChatIdAndIsActiveTrue(chatId);
        if (link.isEmpty()) {
            reply(chatId, "No active project in this group. Use /init first.");
            return;
        }

        Voice voice = msg.voice();
        Optional<TelegramFile> file;
        try {
            file = bot.getFile(voice.file_id());
        } catch (RuntimeException e) {
            log.warn("Voice getFile failed for chat {}: {}", chatId, e.getMessage());
            reply(chatId, "⚠️ Couldn't download voice message.");
            return;
        }
        if (file.isEmpty() || file.get().file_path() == null) {
            reply(chatId, "⚠️ Couldn't download voice message.");
            return;
        }

        byte[] audio;
        try {
            audio = bot.downloadFile(file.get().file_path());
        } catch (RuntimeException e) {
            log.warn("Voice download failed for chat {}: {}", chatId, e.getMessage());
            reply(chatId, "⚠️ Couldn't download voice message.");
            return;
        }
        if (audio == null || audio.length == 0) {
            reply(chatId, "⚠️ Couldn't download voice message.");
            return;
        }

        String transcript;
        try {
            // file_path doubles as a stable filename hint for the upstream transcription service
            // (which expects a filename including extension to infer the audio container).
            transcript = transcription.transcribe(audio, file.get().file_path(), voice.mime_type());
        } catch (RuntimeException e) {
            log.warn("Voice transcription failed for chat {}: {}", chatId, e.getMessage());
            reply(chatId, "⚠️ Couldn't transcribe voice. Try sending text.");
            return;
        }

        log.info("Voice transcribed for chat {} ({} chars)", chatId,
                transcript == null ? 0 : transcript.length());
        // repoUrl is null — repo mapping is a Phase 2 concern; the router treats null as "no repo".
        initiator.initiate(chatId, userId.get(), transcript, link.get(), null);
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
