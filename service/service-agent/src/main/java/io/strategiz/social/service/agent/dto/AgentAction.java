package io.strategiz.social.service.agent.dto;

/**
 * Describes a setup action the frontend should render inline in the chat bubble. When the agent
 * detects that the user is missing a required connection (account, repo, token, device), it returns
 * one or more AgentActions so the frontend can show inline setup cards.
 */
public class AgentAction {

	private String type; // connect_account, grant_repo, add_token, connect_device

	private String platform; // e.g. "twitter", "github", "youtube"

	private String provider; // e.g. "GITHUB", "GITLAB", "BITBUCKET"

	private String tokenProvider; // e.g. "ANTHROPIC", "GITHUB", "OPENAI"

	private String repoFullName; // e.g. "owner/repo"

	private String accessLevel; // e.g. "READ", "WRITE", "ADMIN"

	private String message; // Human-readable explanation

	public AgentAction() {
	}

	public static AgentAction connectAccount(String platform, String message) {
		AgentAction a = new AgentAction();
		a.type = "connect_account";
		a.platform = platform;
		a.message = message;
		return a;
	}

	public static AgentAction grantRepo(String provider, String repoFullName, String accessLevel, String message) {
		AgentAction a = new AgentAction();
		a.type = "grant_repo";
		a.provider = provider;
		a.repoFullName = repoFullName;
		a.accessLevel = accessLevel;
		a.message = message;
		return a;
	}

	public static AgentAction addToken(String tokenProvider, String message) {
		AgentAction a = new AgentAction();
		a.type = "add_token";
		a.tokenProvider = tokenProvider;
		a.message = message;
		return a;
	}

	public static AgentAction connectDevice(String message) {
		AgentAction a = new AgentAction();
		a.type = "connect_device";
		a.message = message;
		return a;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getPlatform() {
		return platform;
	}

	public void setPlatform(String platform) {
		this.platform = platform;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getTokenProvider() {
		return tokenProvider;
	}

	public void setTokenProvider(String tokenProvider) {
		this.tokenProvider = tokenProvider;
	}

	public String getRepoFullName() {
		return repoFullName;
	}

	public void setRepoFullName(String repoFullName) {
		this.repoFullName = repoFullName;
	}

	public String getAccessLevel() {
		return accessLevel;
	}

	public void setAccessLevel(String accessLevel) {
		this.accessLevel = accessLevel;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}
