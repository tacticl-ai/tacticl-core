package io.tacticl.business.pipeline.ingress;

import io.cidadel.framework.exception.CidadelException;
import io.strategiz.social.client.github.config.GitHubConfig;
import io.tacticl.business.profile.service.UserRepoService;
import io.tacticl.data.profile.entity.RepoSource;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkInitiatorSource;
import io.tacticl.data.sparks.entity.SparkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * The transport-neutral front door for the pipeline. Every channel adapter normalizes its native
 * payload into an {@link IngressRequest}; this service then:
 *
 * <ol>
 *   <li>resolves the governing {@link EntryPoint} (narrowest-first, no ask),</li>
 *   <li>authorizes the caller for state-changing kinds (admin-set membership; hard link precond),</li>
 *   <li>materializes any attachments to durable storage (when a materializer is wired), and</li>
 *   <li>routes purely on {@link IngressKind} to the EXISTING pipeline services.</li>
 * </ol>
 *
 * <p>tacticl is ingress-only: orchestration stays in the arbiter. This class reuses
 * {@link PdlcV2Service} unchanged and supplies {@code productId} from the resolved EntryPoint —
 * the registry is the source of the product discriminator on the wire.
 */
@Service
public class IngressDispatchService {

    private static final Logger log = LoggerFactory.getLogger(IngressDispatchService.class);
    private static final String MODULE_NAME = "business-pipeline";

    private final EntryPointResolver entryPointResolver;
    private final SparkService sparkService;
    private final SparkClassifierService sparkClassifierService;
    private final PdlcV2Service pdlcV2Service;
    private final ObjectProvider<ConversationTurnHandler> conversationTurnHandler;
    private final ObjectProvider<AttachmentMaterializer> attachmentMaterializer;
    /** Supplies the resolved Tacticl PAT (github.app-token) for pipeline clone/commit. Optional. */
    private final ObjectProvider<GitHubConfig> gitHubConfig;
    /** Per-user repo memory — auto-registers the repo a build uses. Optional/best-effort. */
    private final ObjectProvider<UserRepoService> userRepoService;

    public IngressDispatchService(EntryPointResolver entryPointResolver,
                                  SparkService sparkService,
                                  SparkClassifierService sparkClassifierService,
                                  PdlcV2Service pdlcV2Service,
                                  ObjectProvider<ConversationTurnHandler> conversationTurnHandler,
                                  ObjectProvider<AttachmentMaterializer> attachmentMaterializer,
                                  ObjectProvider<GitHubConfig> gitHubConfig,
                                  ObjectProvider<UserRepoService> userRepoService) {
        this.entryPointResolver = entryPointResolver;
        this.sparkService = sparkService;
        this.sparkClassifierService = sparkClassifierService;
        this.pdlcV2Service = pdlcV2Service;
        this.conversationTurnHandler = conversationTurnHandler;
        this.attachmentMaterializer = attachmentMaterializer;
        this.gitHubConfig = gitHubConfig;
        this.userRepoService = userRepoService;
    }

    /**
     * Resolves, authorizes, and routes an inbound ingress request.
     *
     * @return the submitted {@link PipelineRun} for EXPLICIT_TRIGGER; empty for all other kinds
     * @throws CidadelException on unresolved entry point, unauthorized caller, or malformed request
     */
    public Optional<PipelineRun> dispatch(IngressRequest request) {
        // EntryPoint resolution is a precondition of the state-changing kinds only —
        // they need its product / repo / admin-set to act. A CONVERSATION_TURN is
        // plain chat with no governing build target, so it must NOT require (and
        // would otherwise be blocked by) a resolvable EntryPoint. Resolve lazily
        // inside each branch that genuinely needs it.
        return switch (request.kind()) {
            case EXPLICIT_TRIGGER ->
                Optional.of(handleExplicitTrigger(request, entryPointResolver.resolve(request.origin())));
            case CHECKPOINT_DECISION -> {
                handleCheckpointDecision(request, entryPointResolver.resolve(request.origin()));
                yield Optional.empty();
            }
            case CANCEL_RUN -> {
                handleCancelRun(request, entryPointResolver.resolve(request.origin()));
                yield Optional.empty();
            }
            case CONVERSATION_TURN -> {
                handleConversationTurn(request);
                yield Optional.empty();
            }
        };
    }

    private PipelineRun handleExplicitTrigger(IngressRequest request, EntryPoint entryPoint) {
        String userId = requireAdmin(request, entryPoint);
        if (request.text() == null || request.text().isBlank()) {
            throw new CidadelException(IngressErrorDetails.INVALID_REQUEST, MODULE_NAME,
                                       request.kind().name(), "missing request text");
        }

        // Materialize attachments to durable storage before submitting, so the run gets stable refs.
        List<String> attachmentRefs = materializeAttachments(request);

        Spark spark = sparkService.create(userId, request.text(),
                                          initiatorSourceFor(request.origin().channel()),
                                          userId,
                                          /* projectId */ null);
        SparkType type = sparkClassifierService.classify(request.text());
        sparkService.classify(spark.getId(), userId, type);

        log.info("Ingress EXPLICIT_TRIGGER channel={} product={} spark={} type={} attachments={}",
                 request.origin().channel(), entryPoint.getProductId(), spark.getId(), type,
                 attachmentRefs.size());

        // Repo: a caller-supplied repoUrl (e.g. the repo the arbiter's create_repo skill just
        // provisioned for a voice build) wins; otherwise fall back to the EntryPoint's repo.
        String repoUrl = (request.repoUrl() != null && !request.repoUrl().isBlank())
            ? request.repoUrl() : entryPoint.getRepoUrl();

        // Token: prefer the resolved Tacticl PAT (Vault github.app-token) so the arbiter can
        // actually clone/commit; fall back to the EntryPoint's (legacy, possibly-unresolved) ref.
        GitHubConfig gh = gitHubConfig.getIfAvailable();
        String githubToken = (gh != null && gh.getAppToken() != null && !gh.getAppToken().isBlank())
            ? gh.getAppToken() : entryPoint.getGithubTokenRef();

        // Per-user repo memory: remember the repo this build uses so the analyst can offer it
        // next time. Best-effort — a registry failure must never block the pipeline submit.
        // A caller-supplied repoUrl means the analyst just provisioned it (CREATED); an
        // EntryPoint-sourced repoUrl is a reused/attached repo (ATTACHED).
        if (repoUrl != null && !repoUrl.isBlank()) {
            UserRepoService repoMemory = userRepoService.getIfAvailable();
            if (repoMemory != null) {
                RepoSource source = (request.repoUrl() != null && !request.repoUrl().isBlank())
                    ? RepoSource.CREATED : RepoSource.ATTACHED;
                try {
                    repoMemory.registerRepoUse(userId, repoUrl, source);
                } catch (Exception e) {
                    log.warn("Repo memory registration failed user={} repo={}: {}", userId, repoUrl, e.toString());
                }
            }
        }

        return pdlcV2Service.submitPipeline(
            entryPoint.getProductId(),
            userId,
            spark.getId(),
            request.text(),
            repoUrl,
            entryPoint.getDefaultPlaybook(),
            /* skipRoles */ List.of(),
            githubToken,
            entryPoint.getCostCeilingUsd()
        );
    }

    private void handleCheckpointDecision(IngressRequest request, EntryPoint entryPoint) {
        String userId = requireAdmin(request, entryPoint);
        CheckpointDecisionPayload decision = request.decision();
        if (decision == null || decision.sparkId() == null || decision.checkpointId() == null
                || decision.decision() == null) {
            throw new CidadelException(IngressErrorDetails.INVALID_REQUEST, MODULE_NAME,
                                       request.kind().name(), "missing or incomplete decision payload");
        }
        pdlcV2Service.resolveCheckpoint(userId, decision.sparkId(), decision.checkpointId(),
                                        decision.decision(), decision.feedback());
        log.info("Ingress CHECKPOINT_DECISION channel={} spark={} checkpoint={} decision={}",
                 request.origin().channel(), decision.sparkId(), decision.checkpointId(),
                 decision.decision());
    }

    private void handleCancelRun(IngressRequest request, EntryPoint entryPoint) {
        String userId = requireAdmin(request, entryPoint);
        CheckpointDecisionPayload decision = request.decision();
        String sparkId = decision != null ? decision.sparkId() : null;
        if (sparkId == null || sparkId.isBlank()) {
            throw new CidadelException(IngressErrorDetails.INVALID_REQUEST, MODULE_NAME,
                                       request.kind().name(), "missing sparkId for cancel");
        }
        pdlcV2Service.cancelPipeline(userId, sparkId);
        log.info("Ingress CANCEL_RUN channel={} spark={}", request.origin().channel(), sparkId);
    }

    private void handleConversationTurn(IngressRequest request) {
        // Conversation turns require a linked identity but not admin rights.
        String userId = requireLinked(request);
        ConversationTurnHandler handler = conversationTurnHandler.getIfAvailable();
        if (handler == null) {
            log.warn("CONVERSATION_TURN received but no ConversationTurnHandler is wired; dropping "
                     + "(channel={} user={})", request.origin().channel(), userId);
            return;
        }
        handler.handleTurn(userId, request.origin(), request.text());
    }

    /** Materializes attachments to durable storage when a materializer is wired; else refs pass through. */
    private List<String> materializeAttachments(IngressRequest request) {
        if (request.attachments().isEmpty()) {
            return List.of();
        }
        AttachmentMaterializer materializer = attachmentMaterializer.getIfAvailable();
        if (materializer != null) {
            return materializer.materialize(request.attachments());
        }
        // TODO(minio): no AttachmentMaterializer wired yet — pass the original channel-native
        // references through so nothing is silently dropped. Replace with MinIO-backed refs once a
        // materializer bean exists.
        return request.attachments().stream()
            .map(a -> a.sourceUrl() != null ? a.sourceUrl() : a.sourceRef())
            .toList();
    }

    /** Enforces the hard account-link precondition: an unlinked identity must never dispatch. */
    private String requireLinked(IngressRequest request) {
        String userId = request.tacticlUserId();
        if (userId == null || userId.isBlank()) {
            throw new CidadelException(IngressErrorDetails.UNLINKED_IDENTITY, MODULE_NAME,
                                       request.origin().channel().name());
        }
        return userId;
    }

    /** Linked AND in the entry point's admin set — required for any state-changing kind. */
    private String requireAdmin(IngressRequest request, EntryPoint entryPoint) {
        String userId = requireLinked(request);
        if (!entryPoint.isAdmin(userId)) {
            throw new CidadelException(IngressErrorDetails.NOT_AUTHORIZED, MODULE_NAME,
                                       userId, entryPoint.getId());
        }
        return userId;
    }

    private SparkInitiatorSource initiatorSourceFor(ChannelType channel) {
        return switch (channel) {
            case DISCORD -> SparkInitiatorSource.DISCORD;
            case TELEGRAM -> SparkInitiatorSource.TELEGRAM_GROUP;
            case VOICE -> SparkInitiatorSource.VOICE;
            case WEB -> SparkInitiatorSource.REST;
        };
    }
}
