package io.tacticl.business.telegram.command;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SPI for summarising a project's pipeline activity for {@code /status}.
 * <p>Injected as {@code Optional} because the real implementation (backed by
 * {@code PipelineStateManager} with project-scoped queries) lands in Chunk 8.
 * Until then, {@link StatusCommand} degrades gracefully.
 */
public interface ProjectPipelineSummaryProvider {

    ProjectPipelineSummary summarize(String projectId);

    record ProjectPipelineSummary(int activeSparks, Instant lastActivity, BigDecimal costToDate) {}
}
