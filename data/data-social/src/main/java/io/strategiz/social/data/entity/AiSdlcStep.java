package io.strategiz.social.data.entity;

/** SDLC step types that Tacticl's AI engine can perform on sparks. */
public enum AiSdlcStep {

	// Classification & Routing
	SPARK_CLASSIFICATION("Classify incoming spark type"),
	TASK_DECOMPOSITION("Break spark into sub-tasks"),

	// Code Lifecycle
	CODE_GENERATION("Write new code"),
	CODE_REVIEW("Review code for quality and bugs"),
	CODE_REFACTORING("Restructure existing code"),
	BUG_DIAGNOSIS("Analyze and identify bug root cause"),
	BUG_FIX("Generate fix for diagnosed bug"),
	TEST_GENERATION("Write tests for code"),
	TEST_EXECUTION("Run and interpret test results"),

	// Content & Communication
	PR_DESCRIPTION("Generate pull request descriptions"),
	DOCUMENTATION("Write or update documentation"),
	COMMIT_MESSAGE("Generate commit messages"),

	// Research & Analysis
	WEB_RESEARCH("Search and synthesize web information"),
	CODE_ANALYSIS("Static analysis and dependency review"),

	// Social & Creative
	SOCIAL_CONTENT("Compose social media posts"),
	CREATIVE_WRITING("Generate creative content"),
	IMAGE_ANALYSIS("Analyze and describe images"),

	// DevOps
	DEPLOYMENT_SCRIPT("Generate or modify deployment configs"),
	MONITORING_ANALYSIS("Analyze logs, metrics, and alerts"),

	// PDLC Pipeline Roles
	REQUIREMENTS_GATHERING("Gather and define requirements and acceptance criteria"),
	SYSTEM_DESIGN("Design system architecture and component breakdown"),
	UI_UX_DESIGN("Design user interface and user experience"),
	SECURITY_REVIEW("Review code for security vulnerabilities"),
	RETROSPECTIVE("Analyze pipeline execution and generate learnings");

	private final String description;

	AiSdlcStep(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}

}
