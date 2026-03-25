package io.strategiz.social.business.agent.pipeline;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.strategiz.social.business.agent.pipeline.PlaybookConfig.CheckpointRule;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineTier;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Registry of all available playbooks. System playbooks are registered at startup via
 * {@link #registerDefaults()}. Future user-defined playbooks can be loaded from Firestore and
 * merged without requiring any architectural changes.
 */
@Service
public class PlaybookRegistry {

	private static final Logger log = LoggerFactory.getLogger(PlaybookRegistry.class);

	// Per-role default timeouts
	private static final Duration PM_TIMEOUT = Duration.ofMinutes(10);
	private static final Duration RESEARCHER_TIMEOUT = Duration.ofMinutes(15);
	private static final Duration ARCHITECT_TIMEOUT = Duration.ofMinutes(15);
	private static final Duration DESIGNER_TIMEOUT = Duration.ofMinutes(10);
	private static final Duration PLANNER_TIMEOUT = Duration.ofMinutes(10);
	private static final Duration IMPLEMENTER_TIMEOUT = Duration.ofMinutes(45);
	private static final Duration REVIEWER_TIMEOUT = Duration.ofMinutes(15);
	private static final Duration TESTER_TIMEOUT = Duration.ofMinutes(30);
	private static final Duration SECURITY_TIMEOUT = Duration.ofMinutes(20);
	private static final Duration TECH_WRITER_TIMEOUT = Duration.ofMinutes(10);
	private static final Duration DEVOPS_TIMEOUT = Duration.ofMinutes(20);

	private final Map<String, PlaybookConfig> playbooks = new LinkedHashMap<>();

	@PostConstruct
	void registerDefaults() {
		register(fullPdlcPlaybook());
		register(bugFixPlaybook());
		register(smallFeaturePlaybook());
		register(refactorPlaybook());
		register(infraChangePlaybook());
		register(docsOnlyPlaybook());
		register(uiChangePlaybook());
		register(securityPatchPlaybook());
		log.info("PlaybookRegistry initialized with {} playbooks: {}", playbooks.size(), playbooks.keySet());
	}

	// --- Public API ---

	public Optional<PlaybookConfig> getPlaybook(String name) {
		return Optional.ofNullable(playbooks.get(name));
	}

	public List<PlaybookConfig> getAllPlaybooks() {
		return List.copyOf(playbooks.values());
	}

	public List<String> getPlaybookNames() {
		return List.copyOf(playbooks.keySet());
	}

	// --- Registration ---

	private void register(PlaybookConfig config) {
		playbooks.put(config.name(), config);
	}

	// --- System Playbook Definitions ---

	private PlaybookConfig fullPdlcPlaybook() {
		List<PlaybookStage> stages = List.of(
				stage(PdlcRole.PM, true, List.of(), List.of(), PM_TIMEOUT),
				stage(PdlcRole.RESEARCHER, true, List.of(PdlcRole.PM), List.of(PdlcRole.PM), RESEARCHER_TIMEOUT),
				stage(PdlcRole.ARCHITECT, true, List.of(PdlcRole.RESEARCHER), List.of(PdlcRole.PM, PdlcRole.RESEARCHER), ARCHITECT_TIMEOUT),
				stage(PdlcRole.DESIGNER, false, List.of(PdlcRole.ARCHITECT), List.of(PdlcRole.ARCHITECT), DESIGNER_TIMEOUT),
				stage(PdlcRole.PLANNER, true, List.of(PdlcRole.ARCHITECT), List.of(PdlcRole.ARCHITECT), PLANNER_TIMEOUT),
				stage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.PLANNER), List.of(PdlcRole.PLANNER, PdlcRole.ARCHITECT), IMPLEMENTER_TIMEOUT),
				stage(PdlcRole.REVIEWER, true, List.of(PdlcRole.IMPLEMENTER), List.of(PdlcRole.IMPLEMENTER), REVIEWER_TIMEOUT),
				stage(PdlcRole.TESTER, true, List.of(PdlcRole.REVIEWER), List.of(PdlcRole.IMPLEMENTER), TESTER_TIMEOUT),
				stage(PdlcRole.SECURITY_ANALYST, true, List.of(PdlcRole.REVIEWER), List.of(PdlcRole.IMPLEMENTER), SECURITY_TIMEOUT),
				stage(PdlcRole.TECHNICAL_WRITER, false, List.of(PdlcRole.TESTER, PdlcRole.SECURITY_ANALYST), List.of(), TECH_WRITER_TIMEOUT),
				stage(PdlcRole.DEVOPS, true, List.of(PdlcRole.TESTER, PdlcRole.SECURITY_ANALYST), List.of(), DEVOPS_TIMEOUT)
		);

		// Tester and SecurityAnalyst run concurrently after Reviewer.
		// TechnicalWriter and DevOps run concurrently after both complete.
		Map<PdlcRole, List<PdlcRole>> parallelGroups = Map.of(
				PdlcRole.TESTER, List.of(PdlcRole.SECURITY_ANALYST),
				PdlcRole.SECURITY_ANALYST, List.of(PdlcRole.TESTER),
				PdlcRole.TECHNICAL_WRITER, List.of(PdlcRole.DEVOPS),
				PdlcRole.DEVOPS, List.of(PdlcRole.TECHNICAL_WRITER)
		);

		// Default checkpoints: after PM, after Architect, after Security (on findings), before DevOps
		Map<PdlcRole, CheckpointRule> checkpoints = Map.of(
				PdlcRole.PM, new CheckpointRule(false, true, false),
				PdlcRole.ARCHITECT, new CheckpointRule(false, true, false),
				PdlcRole.SECURITY_ANALYST, new CheckpointRule(false, false, true),
				PdlcRole.DEVOPS, new CheckpointRule(true, false, false)
		);

		return new PlaybookConfig(
				"FULL_PDLC",
				"Full Product Development Lifecycle",
				"New systems, major features, and multi-component work requiring full role coverage from product definition through deployment.",
				PipelineTier.FULL_PDLC,
				stages,
				parallelGroups,
				checkpoints,
				true
		);
	}

	private PlaybookConfig bugFixPlaybook() {
		List<PlaybookStage> stages = List.of(
				stage(PdlcRole.RESEARCHER, true, List.of(), List.of(), RESEARCHER_TIMEOUT),
				stage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.RESEARCHER), List.of(PdlcRole.RESEARCHER), IMPLEMENTER_TIMEOUT),
				stage(PdlcRole.REVIEWER, true, List.of(PdlcRole.IMPLEMENTER), List.of(PdlcRole.IMPLEMENTER), REVIEWER_TIMEOUT),
				stage(PdlcRole.TESTER, true, List.of(PdlcRole.REVIEWER), List.of(PdlcRole.IMPLEMENTER), TESTER_TIMEOUT)
		);

		return new PlaybookConfig(
				"BUG_FIX",
				"Bug Fix",
				"Known bug requiring diagnosis and a targeted fix — Researcher leads to understand root cause before implementation.",
				PipelineTier.PLAYBOOK,
				stages,
				Map.of(),
				Map.of(),
				true
		);
	}

	private PlaybookConfig smallFeaturePlaybook() {
		List<PlaybookStage> stages = List.of(
				stage(PdlcRole.PM, true, List.of(), List.of(), PM_TIMEOUT),
				stage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.PM), List.of(PdlcRole.PM), IMPLEMENTER_TIMEOUT),
				stage(PdlcRole.REVIEWER, true, List.of(PdlcRole.IMPLEMENTER), List.of(PdlcRole.IMPLEMENTER), REVIEWER_TIMEOUT),
				stage(PdlcRole.TESTER, true, List.of(PdlcRole.REVIEWER), List.of(PdlcRole.IMPLEMENTER), TESTER_TIMEOUT)
		);

		return new PlaybookConfig(
				"SMALL_FEATURE",
				"Small Feature",
				"Clear, bounded feature that fits in a few files — well-defined scope, no architecture changes needed.",
				PipelineTier.PLAYBOOK,
				stages,
				Map.of(),
				Map.of(),
				true
		);
	}

	private PlaybookConfig refactorPlaybook() {
		List<PlaybookStage> stages = List.of(
				stage(PdlcRole.RESEARCHER, true, List.of(), List.of(), RESEARCHER_TIMEOUT),
				stage(PdlcRole.ARCHITECT, true, List.of(PdlcRole.RESEARCHER), List.of(PdlcRole.RESEARCHER), ARCHITECT_TIMEOUT),
				stage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.ARCHITECT), List.of(PdlcRole.ARCHITECT), IMPLEMENTER_TIMEOUT),
				stage(PdlcRole.REVIEWER, true, List.of(PdlcRole.IMPLEMENTER), List.of(PdlcRole.IMPLEMENTER), REVIEWER_TIMEOUT),
				stage(PdlcRole.TESTER, true, List.of(PdlcRole.REVIEWER), List.of(PdlcRole.IMPLEMENTER), TESTER_TIMEOUT)
		);

		return new PlaybookConfig(
				"REFACTOR",
				"Refactor",
				"Code restructuring with design consideration — Researcher analyzes current state, Architect designs target state before implementation.",
				PipelineTier.PLAYBOOK,
				stages,
				Map.of(),
				Map.of(),
				true
		);
	}

	private PlaybookConfig infraChangePlaybook() {
		List<PlaybookStage> stages = List.of(
				stage(PdlcRole.ARCHITECT, true, List.of(), List.of(), ARCHITECT_TIMEOUT),
				stage(PdlcRole.DEVOPS, true, List.of(PdlcRole.ARCHITECT), List.of(PdlcRole.ARCHITECT), DEVOPS_TIMEOUT),
				stage(PdlcRole.SECURITY_ANALYST, true, List.of(PdlcRole.DEVOPS), List.of(PdlcRole.DEVOPS, PdlcRole.ARCHITECT), SECURITY_TIMEOUT)
		);

		return new PlaybookConfig(
				"INFRA_CHANGE",
				"Infrastructure Change",
				"CI/CD, deployment, and infrastructure-only changes — Architect designs, DevOps implements, Security validates.",
				PipelineTier.PLAYBOOK,
				stages,
				Map.of(),
				Map.of(),
				true
		);
	}

	private PlaybookConfig docsOnlyPlaybook() {
		List<PlaybookStage> stages = List.of(
				stage(PdlcRole.RESEARCHER, true, List.of(), List.of(), RESEARCHER_TIMEOUT),
				stage(PdlcRole.TECHNICAL_WRITER, true, List.of(PdlcRole.RESEARCHER), List.of(PdlcRole.RESEARCHER), TECH_WRITER_TIMEOUT)
		);

		return new PlaybookConfig(
				"DOCS_ONLY",
				"Documentation Only",
				"Documentation updates — Researcher gathers context and existing content before Technical Writer produces the output.",
				PipelineTier.PLAYBOOK,
				stages,
				Map.of(),
				Map.of(),
				true
		);
	}

	private PlaybookConfig uiChangePlaybook() {
		List<PlaybookStage> stages = List.of(
				stage(PdlcRole.DESIGNER, true, List.of(), List.of(), DESIGNER_TIMEOUT),
				stage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.DESIGNER), List.of(PdlcRole.DESIGNER), IMPLEMENTER_TIMEOUT),
				stage(PdlcRole.REVIEWER, true, List.of(PdlcRole.IMPLEMENTER), List.of(PdlcRole.IMPLEMENTER), REVIEWER_TIMEOUT),
				stage(PdlcRole.TESTER, true, List.of(PdlcRole.REVIEWER), List.of(PdlcRole.IMPLEMENTER), TESTER_TIMEOUT)
		);

		return new PlaybookConfig(
				"UI_CHANGE",
				"UI Change",
				"Frontend-only work — Designer leads with wireframes and specs before implementation and testing.",
				PipelineTier.PLAYBOOK,
				stages,
				Map.of(),
				Map.of(),
				true
		);
	}

	private PlaybookConfig securityPatchPlaybook() {
		List<PlaybookStage> stages = List.of(
				stage(PdlcRole.SECURITY_ANALYST, true, List.of(), List.of(), SECURITY_TIMEOUT),
				stage(PdlcRole.RESEARCHER, true, List.of(PdlcRole.SECURITY_ANALYST), List.of(PdlcRole.SECURITY_ANALYST), RESEARCHER_TIMEOUT),
				stage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.RESEARCHER), List.of(PdlcRole.RESEARCHER), IMPLEMENTER_TIMEOUT),
				stage(PdlcRole.TESTER, true, List.of(PdlcRole.IMPLEMENTER), List.of(PdlcRole.IMPLEMENTER), TESTER_TIMEOUT),
				stage(PdlcRole.DEVOPS, true, List.of(PdlcRole.TESTER), List.of(), DEVOPS_TIMEOUT)
		);

		return new PlaybookConfig(
				"SECURITY_PATCH",
				"Security Patch",
				"Vulnerability remediation — Security Analyst scopes the threat first, Researcher investigates, Implementer patches, Tester verifies, DevOps deploys urgently.",
				PipelineTier.PLAYBOOK,
				stages,
				Map.of(),
				Map.of(),
				true
		);
	}

	// --- Helpers ---

	private static PlaybookStage stage(
			PdlcRole role,
			boolean required,
			List<PdlcRole> dependsOn,
			List<PdlcRole> canRejectTo,
			Duration timeout
	) {
		return new PlaybookStage(role, required, dependsOn, canRejectTo, timeout);
	}

}
