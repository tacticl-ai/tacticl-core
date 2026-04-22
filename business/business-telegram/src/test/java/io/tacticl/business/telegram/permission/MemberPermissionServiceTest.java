package io.tacticl.business.telegram.permission;

import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramMemberGrantRepository;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MemberPermissionServiceTest {

    @Test
    void ownerRoleInferredFromProjectLink() {
        var projRepo = mock(TelegramProjectLinkRepository.class);
        var grantRepo = mock(TelegramMemberGrantRepository.class);
        var link = TelegramProjectLink.create("p-1", 1L, "u-owner", "G");
        when(projRepo.findByChatIdAndIsActiveTrue(1L)).thenReturn(Optional.of(link));

        var svc = new MemberPermissionService(projRepo, grantRepo);
        assertEquals(MemberRole.OWNER, svc.findRole(1L, "u-owner").orElseThrow());
    }

    @Test
    void defaultsToObserverForUnknownMember() {
        var projRepo = mock(TelegramProjectLinkRepository.class);
        var grantRepo = mock(TelegramMemberGrantRepository.class);
        var link = TelegramProjectLink.create("p-1", 1L, "u-owner", "G");
        when(projRepo.findByChatIdAndIsActiveTrue(1L)).thenReturn(Optional.of(link));
        when(grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue("p-1", "u-x"))
            .thenReturn(Optional.empty());

        var svc = new MemberPermissionService(projRepo, grantRepo);
        assertEquals(MemberRole.OBSERVER, svc.findRole(1L, "u-x").orElseThrow());
    }

    @Test
    void grantUpsertsExistingGrant() {
        var projRepo = mock(TelegramProjectLinkRepository.class);
        var grantRepo = mock(TelegramMemberGrantRepository.class);
        var link = TelegramProjectLink.create("p-1", 1L, "u-owner", "G");
        when(projRepo.findByChatIdAndIsActiveTrue(1L)).thenReturn(Optional.of(link));
        var existing = TelegramMemberGrant.create("p-1", 1L, "u-x", 20L, MemberRole.OBSERVER, "u-owner");
        when(grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue("p-1", "u-x"))
            .thenReturn(Optional.of(existing));

        var svc = new MemberPermissionService(projRepo, grantRepo);
        svc.grant(1L, "u-x", 20L, MemberRole.RUNNER, "u-owner");

        assertEquals(MemberRole.RUNNER, existing.getRole());
        verify(grantRepo).save(existing);
    }

    @Test
    void requireDeniesBelowMinimum() {
        var projRepo = mock(TelegramProjectLinkRepository.class);
        var grantRepo = mock(TelegramMemberGrantRepository.class);
        var link = TelegramProjectLink.create("p-1", 1L, "u-owner", "G");
        when(projRepo.findByChatIdAndIsActiveTrue(1L)).thenReturn(Optional.of(link));
        when(grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue(anyString(), anyString()))
            .thenReturn(Optional.empty());

        var svc = new MemberPermissionService(projRepo, grantRepo);
        PermissionCheck c = svc.require(1L, "u-observer", MemberRole.RUNNER);
        assertFalse(c.allowed());
    }
}
