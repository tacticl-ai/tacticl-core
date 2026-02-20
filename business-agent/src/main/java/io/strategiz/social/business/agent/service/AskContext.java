package io.strategiz.social.business.agent.service;

/** Thread-local context for the currently executing ask/task/agent. */
public class AskContext {

	private static final ThreadLocal<AskContext> CURRENT = new ThreadLocal<>();

	private final String askId;

	private final String taskId;

	private final String agentId;

	public AskContext(String askId, String taskId, String agentId) {
		this.askId = askId;
		this.taskId = taskId;
		this.agentId = agentId;
	}

	public static void set(AskContext ctx) {
		CURRENT.set(ctx);
	}

	public static AskContext get() {
		return CURRENT.get();
	}

	public static void clear() {
		CURRENT.remove();
	}

	public String getAskId() {
		return askId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getAgentId() {
		return agentId;
	}

}
