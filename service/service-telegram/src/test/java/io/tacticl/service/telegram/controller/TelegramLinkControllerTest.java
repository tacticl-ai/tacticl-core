package io.tacticl.service.telegram.controller;

import io.tacticl.business.telegram.TelegramUserLinker;
import io.tacticl.business.telegram.TelegramUserLinker.IssuedLink;
import io.tacticl.data.telegram.entity.TelegramLink;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramLinkControllerTest {

    @Test
    void issueLink_returnsTokenAndBotUrl() {
        var linker = mock(TelegramUserLinker.class);
        when(linker.issueLinkToken("user-1"))
                .thenReturn(new IssuedLink("tok123", "https://t.me/tacticl_bot?start=tok123"));

        var controller = new TelegramLinkController(linker);
        var response = controller.issueLink("user-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("tok123", response.getBody().token());
        assertTrue(response.getBody().botUrl().contains("tok123"));
    }

    @Test
    void status_returnsLinkedChats() {
        var linker = mock(TelegramUserLinker.class);
        var link = TelegramLink.create("user-1", 42L, "alice", "Alice");
        when(linker.linkedChats("user-1")).thenReturn(List.of(link));

        var controller = new TelegramLinkController(linker);
        var response = controller.status("user-1");
        assertEquals(1, response.getBody().linked().size());
        assertEquals(42L, response.getBody().linked().get(0).chatId());
    }

    @Test
    void unlink_returnsNoContent() {
        var linker = mock(TelegramUserLinker.class);
        var controller = new TelegramLinkController(linker);
        ResponseEntity<Void> response = controller.unlink("user-1", 42L);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(linker).unlink("user-1", 42L);
    }
}
