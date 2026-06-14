package io.tacticl.service.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry from a PDLC run's {@code .tacticl/pdlc/{runId}/manifest.json}.
 *
 * <p>The manifest is written by the agents alongside the seven markdown artifacts; each
 * entry describes one committed artifact. Field names mirror the on-disk snake_case keys
 * so the manifest deserializes directly onto this record.
 */
public record ArtifactManifestEntryDto(
    @JsonProperty("artifact_id") String artifactId,
    @JsonProperty("type") String type,
    @JsonProperty("agent") String agent,
    @JsonProperty("path") String path,
    @JsonProperty("title") String title,
    @JsonProperty("summary") String summary
) {}
