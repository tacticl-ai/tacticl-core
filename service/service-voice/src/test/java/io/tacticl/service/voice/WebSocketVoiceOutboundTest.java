package io.tacticl.service.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.tacticl.business.voice.VoiceFrames;
import io.tacticl.business.voice.VoiceState;
import io.tacticl.service.voice.ws.WebSocketVoiceOutbound;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class WebSocketVoiceOutboundTest {

    @Mock
    private WebSocketSession session;

    private WebSocketVoiceOutbound outbound;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("ws-1");
        outbound = new WebSocketVoiceOutbound(session);
    }

    @Test
    void sendControl_stateFrame_writesJsonTextMessage() throws IOException {
        outbound.sendControl(VoiceFrames.state(VoiceState.LISTENING));

        ArgumentCaptor<WebSocketMessage<?>> captor = messageCaptor();
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TextMessage.class);
        String payload = ((TextMessage) captor.getValue()).getPayload();
        assertThat(payload).contains("\"type\":\"state\"").contains("\"state\":\"listening\"");
    }

    @Test
    void sendAudio_nonEmpty_writesBinaryMessage() throws IOException {
        outbound.sendAudio(new byte[] {1, 2, 3, 4});

        ArgumentCaptor<WebSocketMessage<?>> captor = messageCaptor();
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(BinaryMessage.class);
    }

    @Test
    void sendAudio_emptyOrNull_isNoOp() throws IOException {
        outbound.sendAudio(null);
        outbound.sendAudio(new byte[0]);

        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendControl_nullFrame_isNoOp() throws IOException {
        outbound.sendControl(null);

        verify(session, never()).sendMessage(any());
    }

    @Test
    void send_sessionClosed_swallowsAndDoesNotWrite() throws IOException {
        when(session.isOpen()).thenReturn(false);

        outbound.sendAudio(new byte[] {9});
        outbound.sendControl(VoiceFrames.error("boom"));

        verify(session, never()).sendMessage(any());
    }

    @Test
    void send_ioException_isSwallowed() throws IOException {
        org.mockito.Mockito.doThrow(new IOException("closing"))
            .when(session).sendMessage(any());

        // Must not propagate — a dropped frame can't tear down the turn loop.
        outbound.sendControl(VoiceFrames.state(VoiceState.SPEAKING));

        verify(session, times(1)).sendMessage(any());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<WebSocketMessage<?>> messageCaptor() {
        return ArgumentCaptor.forClass((Class<WebSocketMessage<?>>) (Class<?>) WebSocketMessage.class);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }
}
