package io.tacticl.business.telegram.event;

import io.tacticl.business.agent.transcription.TranscriptionService;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.spark.TelegramSparkInitiator;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.dto.TelegramFile;
import io.tacticl.client.telegram.dto.User;
import io.tacticl.client.telegram.dto.Voice;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceMessageHandlerTest {

    private static final long CHAT_ID = -100L;
    private static final long FROM_ID = 42L;

    private TelegramBotClient bot;
    private TranscriptionService transcription;
    private TelegramIdentityResolver identity;
    private TelegramProjectLinkRepository projects;
    private TelegramSparkInitiator initiator;
    private TelegramOutboundQueue outbound;
    private VoiceMessageHandler handler;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBotClient.class);
        transcription = mock(TranscriptionService.class);
        identity = mock(TelegramIdentityResolver.class);
        projects = mock(TelegramProjectLinkRepository.class);
        initiator = mock(TelegramSparkInitiator.class);
        outbound = mock(TelegramOutboundQueue.class);
        handler = new VoiceMessageHandler(bot, transcription, identity, projects, initiator, outbound);
    }

    private static Message voiceMsg(Voice voice) {
        return new Message(1L, 0L,
                new Chat(CHAT_ID, "group", null, null, "Group", false),
                new User(FROM_ID, false, "alice", "First"),
                null,
                null, voice, null, null, null, null, null, false, null);
    }

    @Test
    void unlinkedUser_repliesPromptAndSkipsTranscription() {
        when(identity.resolveByChatId(FROM_ID)).thenReturn(Optional.empty());

        handler.handle(voiceMsg(new Voice("file-1", "uniq-1", 5, "audio/ogg")));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text())
                .isEqualTo("You must link your Tacticl account first.");
        verify(transcription, never()).transcribe(any(), anyString(), anyString());
        verify(initiator, never()).initiate(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void noActiveProject_repliesPromptAndSkipsTranscription() {
        when(identity.resolveByChatId(FROM_ID)).thenReturn(Optional.of("user-alice"));
        when(projects.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        handler.handle(voiceMsg(new Voice("file-1", "uniq-1", 5, "audio/ogg")));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text())
                .isEqualTo("No active project in this group. Use /init first.");
        verify(transcription, never()).transcribe(any(), anyString(), anyString());
        verify(initiator, never()).initiate(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void happyPath_downloadsTranscribesAndInitiatesSpark() {
        when(identity.resolveByChatId(FROM_ID)).thenReturn(Optional.of("user-alice"));
        TelegramProjectLink link = TelegramProjectLink.create("proj-1", CHAT_ID, "user-alice", "Group");
        when(projects.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(bot.getFile("file-1"))
                .thenReturn(Optional.of(new TelegramFile("file-1", "uniq-1", 1234L, "voice/file_5.ogg")));
        byte[] audio = new byte[]{1, 2, 3, 4};
        when(bot.downloadFile("voice/file_5.ogg")).thenReturn(audio);
        when(transcription.transcribe(audio, "voice/file_5.ogg", "audio/ogg")).thenReturn("ship the build");

        handler.handle(voiceMsg(new Voice("file-1", "uniq-1", 5, "audio/ogg")));

        verify(initiator).initiate(eq(CHAT_ID), eq("user-alice"), eq("ship the build"), eq(link), isNull());
        verify(outbound, never()).enqueue(anyLong(), any());
    }

    @Test
    void downloadEmpty_repliesDownloadFailure() {
        when(identity.resolveByChatId(FROM_ID)).thenReturn(Optional.of("user-alice"));
        TelegramProjectLink link = TelegramProjectLink.create("proj-1", CHAT_ID, "user-alice", "Group");
        when(projects.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        // getFile returns empty (Telegram API responded ok=false) — must short-circuit.
        when(bot.getFile("file-1")).thenReturn(Optional.empty());

        handler.handle(voiceMsg(new Voice("file-1", "uniq-1", 5, "audio/ogg")));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text())
                .isEqualTo("⚠️ Couldn't download voice message.");
        verify(transcription, never()).transcribe(any(), anyString(), anyString());
        verify(initiator, never()).initiate(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void transcriptionThrows_repliesTranscriptionFailure() {
        when(identity.resolveByChatId(FROM_ID)).thenReturn(Optional.of("user-alice"));
        TelegramProjectLink link = TelegramProjectLink.create("proj-1", CHAT_ID, "user-alice", "Group");
        when(projects.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(bot.getFile("file-1"))
                .thenReturn(Optional.of(new TelegramFile("file-1", "uniq-1", 1234L, "voice/file_5.ogg")));
        when(bot.downloadFile("voice/file_5.ogg")).thenReturn(new byte[]{1, 2, 3});
        when(transcription.transcribe(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("whisper 503"));

        handler.handle(voiceMsg(new Voice("file-1", "uniq-1", 5, "audio/ogg")));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().text())
                .isEqualTo("⚠️ Couldn't transcribe voice. Try sending text.");
        verify(initiator, never()).initiate(anyLong(), anyString(), anyString(), any(), any());
    }
}
