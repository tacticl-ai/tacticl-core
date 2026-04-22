package io.tacticl.business.telegram.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramUsernameCacheTest {

    @Test
    void observeThenLookupSameCase() {
        var cache = new TelegramUsernameCache();
        cache.observe(1L, 100L, "alice");

        assertThat(cache.lookup(1L, "alice")).contains(100L);
    }

    @Test
    void lookupIsCaseInsensitive() {
        var cache = new TelegramUsernameCache();
        cache.observe(1L, 100L, "Alice");

        assertThat(cache.lookup(1L, "ALICE")).contains(100L);
        assertThat(cache.lookup(1L, "alice")).contains(100L);
        assertThat(cache.lookup(1L, "aLiCe")).contains(100L);
    }

    @Test
    void lookupUnknownReturnsEmpty() {
        var cache = new TelegramUsernameCache();
        cache.observe(1L, 100L, "alice");

        assertThat(cache.lookup(1L, "bob")).isEmpty();
        assertThat(cache.lookup(2L, "alice")).isEmpty(); // wrong chat
        assertThat(cache.lookup(1L, null)).isEmpty();
        assertThat(cache.lookup(1L, "")).isEmpty();
    }

    @Test
    void observeNullOrBlankUsernameIsNoOp() {
        var cache = new TelegramUsernameCache();
        cache.observe(1L, 100L, null);
        cache.observe(1L, 101L, "");
        cache.observe(1L, 102L, "   ");

        assertThat(cache.lookup(1L, "")).isEmpty();
        assertThat(cache.lookup(1L, "   ")).isEmpty();
        // Inner map should not have been created by any of the above.
        assertThat(cache.lookup(1L, "anything")).isEmpty();
    }

    @Test
    void differentChatsIsolated() {
        var cache = new TelegramUsernameCache();
        cache.observe(1L, 100L, "alice");
        cache.observe(2L, 200L, "bob");

        assertThat(cache.lookup(1L, "alice")).contains(100L);
        assertThat(cache.lookup(2L, "bob")).contains(200L);
        assertThat(cache.lookup(1L, "bob")).isEmpty();
        assertThat(cache.lookup(2L, "alice")).isEmpty();
    }
}
