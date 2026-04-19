package io.tacticl.business.telegram;

import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.data.telegram.entity.TelegramLink;
import io.tacticl.data.telegram.entity.TelegramLinkToken;
import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import io.tacticl.data.telegram.repository.TelegramLinkTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramUserLinkerTest {

    TelegramLinkRepository linkRepo;
    TelegramLinkTokenRepository tokenRepo;
    TelegramConfig config;
    TelegramUserLinker linker;

    @BeforeEach
    void setUp() {
        linkRepo = mock(TelegramLinkRepository.class);
        tokenRepo = mock(TelegramLinkTokenRepository.class);
        config = new TelegramConfig();
        config.setBotUsername("tacticl_bot");
        config.setLinkTokenTtlMinutes(15);
        linker = new TelegramUserLinker(linkRepo, tokenRepo, config);
    }

    @Test
    void issueLinkToken_returnsTokenAndDeepLink() {
        var issued = linker.issueLinkToken("user-1");
        assertNotNull(issued.token());
        assertTrue(issued.token().length() >= 24);
        assertTrue(issued.botDeepLinkUrl().startsWith("https://t.me/tacticl_bot?start="));
        verify(tokenRepo).save(any(TelegramLinkToken.class));
    }

    @Test
    void redeemToken_validToken_createsLinkAndReturnsUserId() {
        var token = TelegramLinkToken.create("abc123", "user-1", 15);
        when(tokenRepo.findByToken("abc123")).thenReturn(Optional.of(token));
        when(linkRepo.findByChatId(42L)).thenReturn(Optional.empty());

        Optional<String> userId = linker.redeemToken("abc123", 42L, "alice", "Alice");

        assertEquals(Optional.of("user-1"), userId);
        verify(linkRepo).save(any(TelegramLink.class));
        verify(tokenRepo).save(argThat(t -> ((TelegramLinkToken) t).isConsumed()));
    }

    @Test
    void redeemToken_unknownToken_returnsEmpty() {
        when(tokenRepo.findByToken("nope")).thenReturn(Optional.empty());
        assertTrue(linker.redeemToken("nope", 42L, "x", "X").isEmpty());
    }

    @Test
    void redeemToken_consumedToken_returnsEmpty() {
        var token = TelegramLinkToken.create("abc", "user-1", 15);
        token.consume();
        when(tokenRepo.findByToken("abc")).thenReturn(Optional.of(token));
        assertTrue(linker.redeemToken("abc", 42L, "x", "X").isEmpty());
    }

    @Test
    void redeemToken_expiredToken_returnsEmpty() {
        var token = new TelegramLinkToken();
        token.setToken("abc");
        token.setUserId("user-1");
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(tokenRepo.findByToken("abc")).thenReturn(Optional.of(token));
        assertTrue(linker.redeemToken("abc", 42L, "x", "X").isEmpty());
    }

    @Test
    void redeemToken_chatLinkedToOtherUser_returnsEmpty() {
        var token = TelegramLinkToken.create("abc", "user-1", 15);
        when(tokenRepo.findByToken("abc")).thenReturn(Optional.of(token));
        var otherLink = TelegramLink.create("user-2", 42L, "bob", "Bob");
        when(linkRepo.findByChatId(42L)).thenReturn(Optional.of(otherLink));

        assertTrue(linker.redeemToken("abc", 42L, "alice", "Alice").isEmpty());
    }

    @Test
    void unlink_existingLink_deactivates() {
        var link = TelegramLink.create("user-1", 42L, "alice", "Alice");
        when(linkRepo.findByUserIdAndChatId("user-1", 42L)).thenReturn(Optional.of(link));

        linker.unlink("user-1", 42L);

        verify(linkRepo).save(argThat(l -> !((TelegramLink) l).isActive()));
    }
}
