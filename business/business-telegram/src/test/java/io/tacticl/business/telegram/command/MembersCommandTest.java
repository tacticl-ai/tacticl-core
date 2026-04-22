package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MembersCommandTest {

    private MemberPermissionService permissions;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramOutboundQueue outbound;
    private MembersCommand command;

    private static final long CHAT_ID = -100L;
    private static final long SENDER_TG_ID = 42L;
    private static final String PROJECT_ID = "proj-1";
    private static final String OWNER_ID = "user-alice";

    @BeforeEach
    void setUp() {
        permissions = mock(MemberPermissionService.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        outbound = mock(TelegramOutboundQueue.class);
        command = new MembersCommand(permissions, projectRepo, outbound);
    }

    private static CommandContext groupCtx(String text) {
        Chat chat = new Chat(CHAT_ID, "group", null, null, "My Group");
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(CHAT_ID, SENDER_TG_ID, text, "alice", msg);
    }

    private String capturedReplyText() {
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        return captor.getValue().request().text();
    }

    private static TelegramMemberGrant grant(String userId, MemberRole role) {
        return TelegramMemberGrant.create(PROJECT_ID, CHAT_ID, userId, 0L, role, OWNER_ID);
    }

    @Test
    void handleNoActiveProjectReplies() {
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        command.handle(groupCtx("/members"));

        assertThat(capturedReplyText()).contains("No active project");
    }

    @Test
    void handleEmptyGrantsShowsOwnerOnly() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, OWNER_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(permissions.listGrants(PROJECT_ID)).thenReturn(List.of());

        command.handle(groupCtx("/members"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("Project members")
                .contains("OWNER")
                .contains(OWNER_ID);
    }

    @Test
    void handleMultipleGrantsListsEachRole() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, OWNER_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(permissions.listGrants(PROJECT_ID)).thenReturn(List.of(
                grant("user-bob", MemberRole.RUNNER),
                grant("user-carol", MemberRole.CONTRIBUTOR),
                grant("user-dave", MemberRole.ADMIN)
        ));

        command.handle(groupCtx("/members"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("OWNER")
                .contains(OWNER_ID)
                .contains("RUNNER")
                .contains("user-bob")
                .contains("CONTRIBUTOR")
                .contains("user-carol")
                .contains("ADMIN")
                .contains("user-dave");
    }

    @Test
    void handleTruncatesOverflow() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, OWNER_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));

        List<TelegramMemberGrant> many = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            many.add(grant("user-" + i, MemberRole.CONTRIBUTOR));
        }
        when(permissions.listGrants(PROJECT_ID)).thenReturn(many);

        command.handle(groupCtx("/members"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("user-0")
                .contains("user-49")
                .doesNotContain("user-50")
                .contains("and 25 more");
    }
}
