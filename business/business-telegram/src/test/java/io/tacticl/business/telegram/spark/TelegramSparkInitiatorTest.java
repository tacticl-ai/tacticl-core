package io.tacticl.business.telegram.spark;

import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkInitiatorSource;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

    @Mock SparkService sparkService;
    @Mock PdlcRouter pdlcRouter;
    @Mock MemberPermissionService permissions;
    @Mock TelegramOutboundQueue outbound;

    TelegramSparkInitiator initiator;
    TelegramProjectLink link;

    @BeforeEach
    void setUp() {
        initiator = new TelegramSparkInitiator(sparkService, pdlcRouter, permissions, outbound);
        link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group");
    }

    @Test
    void permissionDeniedRepliesAndSkipsCreate() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.deny(MemberRole.OBSERVER, MemberRole.CONTRIBUTOR, "insufficient role"));

        initiator.initiate(CHAT_ID, USER_ID, "build a REST API", link, REPO_URL);

        verifyNoInteractions(sparkService);
        verifyNoInteractions(pdlcRouter);
        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text()).contains("contributor");
    }

    @Test
    void happyPathCreatesSparkAndRoutes() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));

        Spark spark = Spark.create(USER_ID, "build a REST API");
        spark.setInitiatorSource(SparkInitiatorSource.TELEGRAM_GROUP);
        spark.setInitiatorUserId(USER_ID);
        spark.setProjectId(PROJECT_ID);
        when(sparkService.create(USER_ID, "build a REST API",
                SparkInitiatorSource.TELEGRAM_GROUP, USER_ID, PROJECT_ID))
                .thenReturn(spark);

        PipelineRun run = mock(PipelineRun.class);
        when(pdlcRouter.route(eq(USER_ID), eq(spark.getId()), eq("build a REST API"),
                eq(REPO_URL), any(), any(), any(), anyDouble()))
                .thenReturn(Optional.of(run));

        initiator.initiate(CHAT_ID, USER_ID, "build a REST API", link, REPO_URL);

        verify(sparkService).create(USER_ID, "build a REST API",
                SparkInitiatorSource.TELEGRAM_GROUP, USER_ID, PROJECT_ID);

        verify(pdlcRouter).route(eq(USER_ID), eq(spark.getId()), eq("build a REST API"),
                eq(REPO_URL), any(), eq(List.of()), any(), eq(50.0));

        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text()).contains("Started");
    }

    @Test
    void pdlcDisabledRepliesDisabledMessage() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));

        Spark spark = Spark.create(USER_ID, "build a REST API");
        when(sparkService.create(eq(USER_ID), eq("build a REST API"),
                any(), anyString(), anyString())).thenReturn(spark);
        when(pdlcRouter.route(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyDouble()))
                .thenReturn(Optional.empty());

        initiator.initiate(CHAT_ID, USER_ID, "build a REST API", link, REPO_URL);

        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text())
                .contains("Pipeline engine is disabled");
    }

    @Test
    void routerThrowsRepliesErrorAndStops() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));

        Spark spark = Spark.create(USER_ID, "build a REST API");
        when(sparkService.create(eq(USER_ID), eq("build a REST API"),
                any(), anyString(), anyString())).thenReturn(spark);
        when(pdlcRouter.route(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyDouble()))
                .thenThrow(new RuntimeException("boom"));

        initiator.initiate(CHAT_ID, USER_ID, "build a REST API", link, REPO_URL);

        verify(sparkService).create(eq(USER_ID), eq("build a REST API"),
                any(), anyString(), anyString());

        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound, times(1)).enqueue(eq(CHAT_ID), msg.capture());
        String replyText = msg.getValue().request().text();
        assertThat(replyText).contains("Couldn't start");
        assertThat(replyText).doesNotContain("Started");
    }

    @Test
    void blankTextRepliesAndSkipsCreate() {
        initiator.initiate(CHAT_ID, USER_ID, "   ", link, REPO_URL);

        verifyNoInteractions(sparkService);
        verifyNoInteractions(pdlcRouter);
        verifyNoInteractions(permissions);
        ArgumentCaptor<OutboundMessage> msg = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound, times(1)).enqueue(eq(CHAT_ID), msg.capture());
        assertThat(msg.getValue().request().text()).contains("Spark text is required");
    }

    @Test
    void tooLongTextIsRejectedBeforeCreate() {
        String tooLong = "a".repeat(2001);

        initiator.initiate(CHAT_ID, USER_ID, tooLong, link, REPO_URL);

        verifyNoInteractions(sparkService);
        verifyNoInteractions(pdlcRouter);
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
        Spark spark = Spark.create(USER_ID, atCap);
        when(sparkService.create(eq(USER_ID), eq(atCap),
                any(), anyString(), anyString())).thenReturn(spark);
        when(pdlcRouter.route(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyDouble()))
                .thenReturn(Optional.of(mock(PipelineRun.class)));

        initiator.initiate(CHAT_ID, USER_ID, atCap, link, REPO_URL);

        verify(sparkService).create(eq(USER_ID), eq(atCap), any(), anyString(), anyString());
    }

    @Test
    void nullRepoUrlStillRoutes() {
        when(permissions.require(CHAT_ID, USER_ID, MemberRole.CONTRIBUTOR))
                .thenReturn(PermissionCheck.allow(MemberRole.CONTRIBUTOR));

        Spark spark = Spark.create(USER_ID, "refactor module");
        when(sparkService.create(eq(USER_ID), eq("refactor module"),
                any(), anyString(), anyString())).thenReturn(spark);
        when(pdlcRouter.route(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyDouble()))
                .thenReturn(Optional.of(mock(PipelineRun.class)));

        initiator.initiate(CHAT_ID, USER_ID, "refactor module", link, null);

        verify(pdlcRouter).route(eq(USER_ID), eq(spark.getId()), eq("refactor module"),
                eq(null), any(), eq(List.of()), any(), eq(50.0));
    }
}
