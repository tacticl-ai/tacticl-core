package io.tacticl.business.telegram.identity;

import io.tacticl.data.telegram.entity.TelegramLink;
import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class TelegramIdentityResolverTest {

    @Test
    void resolveByChatIdReturnsUserIdForActiveLink() {
        var repo = mock(TelegramLinkRepository.class);
        var link = TelegramLink.create("u-7", 99L, "alice", "Alice");
        when(repo.findByChatIdAndIsActiveTrue(99L)).thenReturn(Optional.of(link));

        var resolver = new TelegramIdentityResolver(repo);
        assertEquals(Optional.of("u-7"), resolver.resolveByChatId(99L));
    }

    @Test
    void unknownChatReturnsEmpty() {
        var repo = mock(TelegramLinkRepository.class);
        when(repo.findByChatIdAndIsActiveTrue(anyLong())).thenReturn(Optional.empty());

        var resolver = new TelegramIdentityResolver(repo);
        assertTrue(resolver.resolveByChatId(99L).isEmpty());
    }

    @Test
    void softDeletedLinkReturnsEmpty() {
        // After /unlink the row is isActive=false; findByChatIdAndIsActiveTrue yields empty
        // so the resolver denies auth — this is the regression guard for the unlink bypass.
        var repo = mock(TelegramLinkRepository.class);
        when(repo.findByChatIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        var resolver = new TelegramIdentityResolver(repo);
        assertTrue(resolver.resolveByChatId(99L).isEmpty());
        verify(repo, never()).findByChatId(anyLong());
    }
}
