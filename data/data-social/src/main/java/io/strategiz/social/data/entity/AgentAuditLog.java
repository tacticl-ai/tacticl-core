package io.strategiz.social.data.entity;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.identity.data.base.annotation.Collection;
import io.cidadel.identity.data.base.entity.BaseEntity;

/**
 * Audit log entry for every agent command executed. Stored in the agent_audit_log
 * Firestore collection.
 */
@IgnoreExtraProperties
@Collection("agent_audit_log")
public class AgentAuditLog extends BaseEntity {

	private String id;

	private String userId;

	private String sessionId;

	private String commandText;

	private String channelId;

	private List<String> toolsInvoked = new ArrayList<>();

	private String responseText;

	private boolean success;

	private String errorMessage;

	private int tokenCount;

	private long executionTimeMs;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getCommandText() {
		return commandText;
	}

	public void setCommandText(String commandText) {
		this.commandText = commandText;
	}

	public String getChannelId() {
		return channelId;
	}

	public void setChannelId(String channelId) {
		this.channelId = channelId;
	}

	public List<String> getToolsInvoked() {
		return toolsInvoked;
	}

	public void setToolsInvoked(List<String> toolsInvoked) {
		this.toolsInvoked = toolsInvoked;
	}

	public String getResponseText() {
		return responseText;
	}

	public void setResponseText(String responseText) {
		this.responseText = responseText;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public int getTokenCount() {
		return tokenCount;
	}

	public void setTokenCount(int tokenCount) {
		this.tokenCount = tokenCount;
	}

	public long getExecutionTimeMs() {
		return executionTimeMs;
	}

	public void setExecutionTimeMs(long executionTimeMs) {
		this.executionTimeMs = executionTimeMs;
	}

}
