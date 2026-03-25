package io.strategiz.social.business.agent.pipeline;

/**
 * Thrown when a pipeline checkpoint is not resolved within the allowed timeout window.
 * The pipeline is subsequently marked as FAILED by the orchestrator.
 */
public class CheckpointTimeoutException extends RuntimeException {

	public CheckpointTimeoutException(String message) {
		super(message);
	}

}
