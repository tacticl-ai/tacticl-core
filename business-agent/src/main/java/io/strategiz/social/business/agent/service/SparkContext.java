package io.strategiz.social.business.agent.service;

/** Thread-local context for the currently executing spark. */
public class SparkContext {

	private static final ThreadLocal<SparkContext> CURRENT = new ThreadLocal<>();

	private final String sparkId;

	public SparkContext(String sparkId) {
		this.sparkId = sparkId;
	}

	public static void set(SparkContext ctx) {
		CURRENT.set(ctx);
	}

	public static SparkContext get() {
		return CURRENT.get();
	}

	public static void clear() {
		CURRENT.remove();
	}

	public String getSparkId() {
		return sparkId;
	}

}
