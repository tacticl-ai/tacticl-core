package io.tacticl.business.pipeline.service;

import io.cidadel.framework.exception.CidadelException;
import io.strategiz.social.client.github.GitHubClient;
import io.strategiz.social.client.github.config.GitHubConfig;
import io.strategiz.social.client.github.model.GitHubFileContent;
import io.tacticl.data.pipeline.entity.PipelineRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Retrieves the seven PDLC markdown artifacts a pipeline's agents commit to GitHub.
 *
 * <p>Artifacts live at {@code .tacticl/pdlc/{runId}/<name>.md} with a sibling
 * {@code .tacticl/pdlc/{runId}/manifest.json}, where {@code runId == PipelineRun.getId()}.
 * Before a run's PR merges, the files sit on the per-run PR branch (e.g.
 * {@code pdlc/fix/<intakeId>}); after merge they live on the repo's default branch.
 *
 * <h2>Branch resolution (chosen strategy)</h2>
 * {@link PipelineRun} currently stores no branch / prUrl / headSha, so this service tries a
 * <em>candidate ref list</em> in order and returns the first that resolves:
 * <ol>
 *   <li>an explicit branch passed by the caller (when known), then</li>
 *   <li>the repo's <b>default branch</b> — addressed by passing an empty {@code ref} to the
 *       GitHub contents API, which resolves the default ref (HEAD). This is the post-merge case
 *       and the safe fallback.</li>
 * </ol>
 * When a stored branch becomes available on {@link PipelineRun} (e.g. {@code prUrl}/{@code headSha}),
 * add it as the first candidate ahead of the default-branch fallback.
 *
 * <p>The {@link GitHubClient}/{@link GitHubConfig} beans are conditional on
 * {@code tacticl.github.enabled=true}; they are injected via {@link ObjectProvider} so the rest of
 * the pipeline keeps working when GitHub is disabled (artifact retrieval then returns empty).
 */
@Service
public class ArtifactRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactRetrievalService.class);
    private static final JsonMapper JSON = new JsonMapper();

    /** Base path of the per-run artifact directory inside the repo. */
    private static final String PDLC_BASE = ".tacticl/pdlc/";
    private static final String MANIFEST = "manifest.json";

    /** Matches https://github.com/<owner>/<repo> with an optional .git suffix / trailing slash. */
    private static final Pattern REPO_URL = Pattern.compile(
        "github\\.com[:/]+([^/]+)/([^/]+?)(?:\\.git)?/?$");

    /** Supplies the GitHub REST client; absent when {@code tacticl.github.enabled=false}. */
    private final ObjectProvider<GitHubClient> gitHubClient;
    /** Supplies the resolved Tacticl PAT (Vault {@code github.app-token}); same source the submit path uses. */
    private final ObjectProvider<GitHubConfig> gitHubConfig;

    public ArtifactRetrievalService(ObjectProvider<GitHubClient> gitHubClient,
                                    ObjectProvider<GitHubConfig> gitHubConfig) {
        this.gitHubClient = gitHubClient;
        this.gitHubConfig = gitHubConfig;
    }

    /**
     * Parse a manifest into entries. Returns the raw JSON node list as {@link ManifestEntry}
     * records so the service layer can map to its own DTO without depending on this module's types.
     *
     * @return the manifest entries, or empty when GitHub is disabled / the manifest is unreadable
     */
    public List<ManifestEntry> listArtifacts(PipelineRun run) {
        GitHubClient client = gitHubClient.getIfAvailable();
        String token = resolveToken();
        String repo = resolveRepo(run);
        if (client == null || token == null || repo == null) {
            log.warn("Artifact manifest unavailable (githubEnabled={}, tokenPresent={}, repoUrl={})",
                     client != null, token != null, run.getRepoUrl());
            return List.of();
        }

        String manifestPath = PDLC_BASE + run.getId() + "/" + MANIFEST;
        Optional<GitHubFileContent> fileOpt = readFirstResolvable(client, repo, manifestPath, token);
        if (fileOpt.isEmpty()) {
            log.info("No manifest at {} in {} on any candidate ref", manifestPath, repo);
            return List.of();
        }

        String raw = fileOpt.get().getDecodedContent();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            var node = JSON.readTree(raw);
            // Accept either a top-level array or an object with an "artifacts" array.
            var arr = node.isArray() ? node : node.path("artifacts");
            List<ManifestEntry> entries = new ArrayList<>();
            if (arr.isArray()) {
                for (var e : arr) {
                    entries.add(new ManifestEntry(
                        e.path("artifact_id").asString(""),
                        e.path("type").asString(""),
                        e.path("agent").asString(""),
                        e.path("path").asString(""),
                        e.path("title").asString(""),
                        e.path("summary").asString("")
                    ));
                }
            }
            return entries;
        } catch (Exception e) {
            log.error("Failed to parse manifest at {} in {}: {}", manifestPath, repo, e.toString());
            return List.of();
        }
    }

    /**
     * Read one markdown artifact by its basename (without {@code .md}), e.g. {@code "prd"}.
     *
     * @return the decoded markdown + blob sha, or empty when unreadable / GitHub disabled
     */
    public Optional<ArtifactContent> readArtifact(PipelineRun run, String name) {
        GitHubClient client = gitHubClient.getIfAvailable();
        String token = resolveToken();
        String repo = resolveRepo(run);
        if (client == null || token == null || repo == null) {
            log.warn("Artifact content unavailable (githubEnabled={}, tokenPresent={}, repoUrl={})",
                     client != null, token != null, run.getRepoUrl());
            return Optional.empty();
        }

        String safeName = sanitizeName(name);
        String path = PDLC_BASE + run.getId() + "/" + safeName + ".md";
        return readFirstResolvable(client, repo, path, token)
            .map(f -> new ArtifactContent(safeName, f.getDecodedContent(), f.getSha()));
    }

    /**
     * Try the candidate refs in order, returning the first file that resolves.
     * Candidate order: (future stored branch first), then the repo default branch (empty ref).
     */
    private Optional<GitHubFileContent> readFirstResolvable(GitHubClient client, String repo,
                                                            String path, String token) {
        for (String ref : candidateRefs()) {
            try {
                GitHubFileContent file = client.readFile(repo, path, ref, token);
                if (file != null) {
                    return Optional.of(file);
                }
            } catch (CidadelException e) {
                // NOT_FOUND on this ref → try the next candidate; any other error is logged + skipped.
                log.debug("readFile miss repo={} path={} ref='{}': {}", repo, path, ref, e.getMessage());
            } catch (Exception e) {
                log.warn("readFile error repo={} path={} ref='{}': {}", repo, path, ref, e.toString());
            }
        }
        return Optional.empty();
    }

    /**
     * Ordered candidate refs. {@link PipelineRun} stores no branch today, so this is just the
     * default branch (empty ref → GitHub resolves HEAD). Prepend a stored PR branch here once
     * {@link PipelineRun} carries one.
     */
    private List<String> candidateRefs() {
        // Empty ref resolves the repo default branch on the GitHub contents API.
        return List.of("");
    }

    /** Reuse the same token source the submit path uses: GitHubConfig.appToken (Vault github.app-token). */
    private String resolveToken() {
        GitHubConfig gh = gitHubConfig.getIfAvailable();
        if (gh != null && gh.getAppToken() != null && !gh.getAppToken().isBlank()) {
            return gh.getAppToken();
        }
        return null;
    }

    /**
     * Resolve the repo name (without owner prefix) from the run's repoUrl.
     *
     * <p>{@link GitHubClient} is pre-bound to a fixed owner ({@code github.owner}) and takes the
     * bare repo name, so only the {@code <repo>} segment is needed.
     */
    private String resolveRepo(PipelineRun run) {
        String url = run.getRepoUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher m = REPO_URL.matcher(url.trim());
        if (m.find()) {
            return m.group(2);
        }
        return null;
    }

    private String sanitizeName(String name) {
        if (name == null) return "";
        // Defensive: callers pass a basename; strip any path components and a trailing .md.
        String base = name.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        if (base.endsWith(".md")) base = base.substring(0, base.length() - 3);
        return base;
    }

    /** Transport record for a manifest entry; service layer maps to its own DTO. */
    public record ManifestEntry(String artifactId, String type, String agent,
                                String path, String title, String summary) {}

    /** Transport record for one decoded markdown artifact. */
    public record ArtifactContent(String name, String markdown, String sha) {}
}
