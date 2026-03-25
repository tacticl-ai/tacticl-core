package io.strategiz.social.service.agent.dto;

import io.strategiz.social.business.agent.pipeline.PlaybookConfig;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineTier;

import java.util.List;

/** Response DTO for a playbook configuration. */
public class PlaybookResponse {

	private String name;

	private String displayName;

	private String description;

	private PipelineTier tier;

	private List<String> stages;

	private boolean isSystemPlaybook;

	public static PlaybookResponse from(PlaybookConfig config) {
		PlaybookResponse response = new PlaybookResponse();
		response.setName(config.name());
		response.setDisplayName(config.displayName());
		response.setDescription(config.description());
		response.setTier(config.tier());
		response.setSystemPlaybook(config.isSystemPlaybook());

		List<String> stageNames = config.stages().stream()
			.map(stage -> stage.role().name())
			.toList();
		response.setStages(stageNames);

		return response;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public PipelineTier getTier() {
		return tier;
	}

	public void setTier(PipelineTier tier) {
		this.tier = tier;
	}

	public List<String> getStages() {
		return stages;
	}

	public void setStages(List<String> stages) {
		this.stages = stages;
	}

	public boolean isSystemPlaybook() {
		return isSystemPlaybook;
	}

	public void setSystemPlaybook(boolean isSystemPlaybook) {
		this.isSystemPlaybook = isSystemPlaybook;
	}

}
