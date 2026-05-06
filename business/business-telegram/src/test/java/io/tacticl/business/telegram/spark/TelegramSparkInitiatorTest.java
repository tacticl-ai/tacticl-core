package io.tacticl.business.telegram.spark;

import io.tacticl.business.agent.command.AgentCommand;
import io.tacticl.business.agent.command.AgentCommandResult;
import io.tacticl.business.agent.command.AgentCommandService;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramSparkInitiatorTest {

    private static final long CHAT_ID = 123456L;
    private static final String USER_ID = "user-alice";
    private static final String PROJECT_ID = "project-1";
    private static final String REPO_URL = "https://github.com/acme/repo";

    @Mock AgentCommandService agentCommandService;
    @Mock MemberPermissionService permissions;
    @Mock TelegramOutboundQueue outbound;

    TelegramSparkInitiator initiator;
    TelegramProjectLink link;

    @BeforeEach
    void setUp() {
        initiator = new TelegramSparkInitiator(agentCommandService, permissions, outbound);
        link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group");
    }

    @Test
    void permissionDeniedRepliesAndSkipsExecute() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.deny(MemberRole.OBSERVER, MemberRole.CONTRIBUTOR, "insufficient role"));

        initiator.initiate(CHAT_ID, USER_ID, "build a REST API", link, REPO_URL);

        verifyNoInteractions(agentCommandService);
        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text()).contains("contributor");
    }

    @Test
    void happyPathDelegatesAndRepliesStarted() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        when(agentCommandService.execute(any(AgentCommand.class)))
                .thenReturn(AgentCommandResult.pipeline("spark-1", "run-1", "FULL_PDLC"));

        initiator.initiate(CHAT_ID, USER_ID, "build a REST API", link, REPO_URL);

        ArgumentCaptor<AgentCommand> captor = ArgumentCaptor.forClass(AgentCommand.class);
        verify(agentCommandService).execute(captor.capture());
        AgentCommand cmd = captor.getValue();
        assertThat(cmd.userId()).isEqualTo(USER_ID);
        assertThat(cmd.text()).isEqualTo("build a REST API");
        assertThat(cmd.projectId()).isEqualTo(PROJECT_ID);
        assertThat(cmd.repoUrl()).isEqualTo(REPO_URL);

        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text()).contains("Started");
    }

    @Test
    void cloudCompletionRepliesWithResponseText() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        when(agentCommandService.execute(any(AgentCommand.class)))
                .thenReturn(AgentCommandResult.cloudCompleted("spark-2", "Here you go!", "claude-sonnet-4-6", 7));

        initiator.initiate(CHAT_ID, USER_ID, "summarize the news", link, null);

        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text()).isEqualTo("Here you go!");
    }

    @Test
    void serviceThrowsRepliesFriendlyError() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        when(agentCommandService.execute(any(AgentCommand.class)))
                .thenThrow(new RuntimeException("boom"));

        initiator.initiate(CHAT_ID, USER_ID, "build a REST API", link, REPO_URL);

        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound, times(1)).enqueue(eq(CHAT_ID), msg.capture());
        String replyText = msg.getValue().request().text();
        assertThat(replyText).contains("Couldn't start");
        assertThat(replyText).doesNotContain("Started");
    }

    @Test
    void cloudFailureRepliesWithWarning() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        when(agentCommandService.execute(any(AgentCommand.class)))
                .thenReturn(AgentCommandResult.cloudFailed("spark-3", "Try again later."));

        initiator.initiate(CHAT_ID, USER_ID, "do something", link, null);

        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text()).contains("Try again later");
    }

    @Test
    void blankTextRepliesAndSkipsExecute() {
        initiator.initiate(CHAT_ID, USER_ID, "   ", link, REPO_URL);

        verifyNoInteractions(agentCommandService);
        verifyNoInteractions(permissions);
        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound, times(1)).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text()).contains("Spark text is required");
    }

    @Test
    void tooLongTextIsRejectedBeforeExecute() {
        String tooLong = "a".repeat(2001);

        initiator.initiate(CHAT_ID, USER_ID, tooLong, link, REPO_URL);

        verifyNoInteractions(agentCommandService);
        verifyNoInteractions(permissions);
        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound, times(1)).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text()).contains("too long");
        assertThat(msg.getValue().request().text()).contains("2000");
    }

    @Test
    void textAtCapIsAccepted() {
        String atCap = "a".repeat(2000);
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));
        when(agentCommandService.execute(any(AgentCommand.class)))
                .thenReturn(AgentCommandResult.pipeline("s", "r", "FULL_PDLC"));

        initiator.initiate(CHAT_ID, USER_ID, atCap, link, REPO_URL);

        verify(agentCommandService).execute(any(AgentCommand.class));
    }
}
