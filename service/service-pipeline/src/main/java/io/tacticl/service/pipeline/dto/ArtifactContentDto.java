package io.tacticl.service.pipeline.dto;

/**
 * Decoded content of a single PDLC markdown artifact
 * ({@code .tacticl/pdlc/{runId}/<name>.md}) retrieved from GitHub.
 *
 * @param name     file basename without the {@code .md} suffix (e.g. {@code "prd"})
 * @param markdown the decoded UTF-8 markdown body
 * @param sha      the GitHub blob SHA of the file at the resolved ref
 */
public record ArtifactContentDto(
    String name,
    String markdown,
    String sha
) {}
