package io.tacticl.service.telegram.controller;

import io.tacticl.business.telegram.TelegramDispatchService;
import io.tacticl.business.telegram.TelegramWebhookSecurity;
import io.tacticl.client.telegram.dto.Update;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelegramWebhookControllerTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final byte[] VALID_BODY = "{\"update_id\":1}".getBytes();

    @Test
    void webhook_invalidSignature_returns401() {
        var security = mock(TelegramWebhookSecurity.class);
        var dispatch = mock(TelegramDispatchService.class);
        when(security.isValidSignature("bad")).thenReturn(false);

        var controller = new TelegramWebhookController(security, dispatch, MAPPER);
        ResponseEntity<Void> response = controller.webhook("bad", VALID_BODY);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(dispatch);
    }

    @Test
    void webhook_invalidSignature_skipsBodyParsing() {
        var security = mock(TelegramWebhookSecurity.class);
        var dispatch = mock(TelegramDispatchService.class);
        when(security.isValidSignature(any())).thenReturn(false);

        var controller = new TelegramWebhookController(security, dispatch, MAPPER);
        ResponseEntity<Void> response = controller.webhook(null, "not-json".getBytes());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(dispatch);
    }

    @Test
    void webhook_validSignature_dispatchesAndReturns200() {
        var security = mock(TelegramWebhookSecurity.class);
        var dispatch = mock(TelegramDispatchService.class);
        when(security.isValidSignature("good")).thenReturn(true);

        var controller = new TelegramWebhookController(security, dispatch, MAPPER);
        ResponseEntity<Void> response = controller.webhook("good", VALID_BODY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(dispatch).handle(any(Update.class));
    }

    @Test
    void webhook_dispatchThrows_stillReturns200() {
        var security = mock(TelegramWebhookSecurity.class);
        var dispatch = mock(TelegramDispatchService.class);
        when(security.isValidSignature(any())).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(dispatch).handle(any());

        var controller = new TelegramWebhookController(security, dispatch, MAPPER);
        ResponseEntity<Void> response = controller.webhook("good", VALID_BODY);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
