package io.strategiz.social.business.agent.ai;

import io.strategiz.social.data.entity.AiSdlcStep;

public final class AiSparkTypeStepMapper {

	private AiSparkTypeStepMapper() {}

	public static AiSdlcStep mapToStep(String sparkType) {
		return switch (sparkType != null ? sparkType.toLowerCase().trim() : "code") {
			case "code"     -> AiSdlcStep.CODE_GENERATION;
			case "social"   -> AiSdlcStep.SOCIAL_CONTENT;
			case "research" -> AiSdlcStep.WEB_RESEARCH;
			case "devops"   -> AiSdlcStep.DEPLOYMENT_SCRIPT;
			case "creative" -> AiSdlcStep.CREATIVE_WRITING;
			case "data"     -> AiSdlcStep.CODE_ANALYSIS;
			default         -> AiSdlcStep.CODE_GENERATION;
		};
	}

}
