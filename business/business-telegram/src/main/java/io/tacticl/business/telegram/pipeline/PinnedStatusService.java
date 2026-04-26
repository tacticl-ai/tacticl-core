package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.telegram.command.ProjectPipelineSummaryProvider;
import io.tacticl.business.telegram.command.ProjectPipelineSummaryProvider.ProjectPipelineSummary;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.SendMessageResponse;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Debounced per-chat "pinned project status" message.
 *
 * <p>Callers fire-and-forget {@link #requestStatusUpdate(long)} on any pipeline event.
 * The service coalesces bursts so Telegram only sees <em>at most one</em> edit per
 * chat every ~2 seconds of quiet, with a hard ceiling of 10 s from the first request
 * in any continuous burst. Content is re-read from the summary provider at flush
 * time, so the pinned message always reflects current state — stale enqueued text is
 * never sent.
 *
 * <p>On the first flush for a chat we send + pin a fresh message and persist the
 * resulting {@code messageId} on the active {@link TelegramProjectLink}. All
 * subsequent flushes call {@code editMessageText} against that stored id.
 *
 * <p>Bot API failures are logged and the pending entry is dropped — never retried —
 * so one bad chat cannot wedge the drain loop for the rest.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class PinnedStatusService {

    private static final Logger log = LoggerFactory.getLogger(PinnedStatusService.class);

    /** Hard ceiling from the first request in a burst — guards against starvation under continuous activity. */
    private static final Duration DEBOUNCE_WINDOW = Duration.ofSeconds(10);

    /** Common-case flush trigger — fire once the burst has subsided. */
    private static final Duration QUIET_WINDOW = Duration.ofSeconds(2);

    private final TelegramBotClient bot;
    private final TelegramProjectLinkRepository projectRepo;
    private final Optional<ProjectPipelineSummaryProvider> summaryProvider;
    private final Clock clock;

    private final ConcurrentHashMap<Long, AtomicReference<PendingStatus>> pending = new ConcurrentHashMap<>();

    @Autowired
    public PinnedStatusService(TelegramBotClient bot,
                               TelegramProjectLinkRepository projectRepo,
                               Optional<ProjectPipelineSummaryProvider> summaryProvider) {
        this(bot, projectRepo, summaryProvider, Clock.systemUTC());
    }

    // Package-private ctor for tests — allows a deterministic clock without a dedicated bean.
    PinnedStatusService(TelegramBotClient bot,
                        TelegramProjectLinkRepository projectRepo,
                        Optional<ProjectPipelineSummaryProvider> summaryProvider,
                        Clock clock) {
        this.bot = bot;
        this.projectRepo = projectRepo;
        this.summaryProvider = summaryProvider;
        this.clock = clock;
    }

    /**
     * Record intent to refresh the pinned status for {@code chatId}. Returns immediately;
     * the actual Telegram API call happens later via {@link #drain()}.
     */
    public void requestStatusUpdate(long chatId) {
        Instant now = clock.instant();
        pending.compute(chatId, (id, existing) -> {
            if (existing == null) {
                return new AtomicReference<>(new PendingStatus(id, now, now));
            }
            PendingStatus prior = existing.get();
            // WHY: keep the original firstRequestedAt so the hard-cap still fires under steady load.
            existing.set(new PendingStatus(id, prior.firstRequestedAt(), now));
            return existing;
        });
    }

    @Scheduled(fixedDelay = 2_000L)
    public void drain() {
        if (pending.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        for (Map.Entry<Long, AtomicReference<PendingStatus>> entry : pending.entrySet()) {
            PendingStatus ps = entry.getValue().get();
            if (ps == null) {
                continue;
            }
            boolean hardCapHit = Duration.between(ps.firstRequestedAt(), now).compareTo(DEBOUNCE_WINDOW) >= 0;
            boolean quiet = Duration.between(ps.lastRequestedAt(), now).compareTo(QUIET_WINDOW) >= 0;
            // WHY: two conditions — quiet window is the common case; hard cap guards against
            // continuous activity that would otherwise never let the quiet window elapse.
            if (!hardCapHit && !quiet) {
                continue;
            }
            long chatId = entry.getKey();
            try {
                flushFor(chatId);
            } catch (RuntimeException e) {
                // WHY: one bad chat must not poison the drain loop for the rest.
                log.warn("pinned status: flush failed for chat {} — dropping", chatId, e);
            } finally {
                pending.remove(chatId);
            }
        }
    }

    private void flushFor(long chatId) {
        if (summaryProvider.isEmpty()) {
            return;
        }
        Optional<TelegramProjectLink> linkOpt = projectRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            return;
        }
        TelegramProjectLink link = linkOpt.get();
        ProjectPipelineSummary summary = summaryProvider.get().summarize(link.getProjectId());
        if (summary == null) {
            return;
        }
        String content = renderContent(summary);
        if (link.getPinnedStatusMessageId() == null) {
            SendMessageResponse response = bot.sendMessage(SendMessageRequest.plain(chatId, content));
            long messageId = response.message_id();
            bot.pinChatMessage(chatId, messageId);
            link.setPinnedStatusMessageId(messageId);
            projectRepo.save(link);
        } else {
            bot.editMessageText(chatId, link.getPinnedStatusMessageId(), content, null);
        }
    }

    private static String renderContent(ProjectPipelineSummary summary) {
        // WHY: phase + pendingCheckpoints are intentionally omitted; ProjectPipelineSummary
        // does not expose them yet. Extending the SPI is cross-cutting — tracked as a
        // follow-up so this task stays scoped to the debouncer.
        String lastActivity = summary.lastActivity() != null ? summary.lastActivity().toString() : "—";
        BigDecimal cost = summary.costToDate() != null ? summary.costToDate() : BigDecimal.ZERO;
        return "📋 Project status\n"
            + "Active sparks: " + summary.activeSparks() + "\n"
            + "Last activity: " + lastActivity + "\n"
            + "Spend-to-date: " + String.format(Locale.ROOT, "$%.2f", cost);
    }

    private record PendingStatus(long chatId, Instant firstRequestedAt, Instant lastRequestedAt) {}
}
