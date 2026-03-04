package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.CommandState;
import io.strategiz.social.data.entity.CommandType;
import io.strategiz.social.data.entity.DeviceCommand;
import io.strategiz.social.data.repository.DeviceCommandRepository;
import com.google.cloud.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creates, dispatches, and tracks device commands. Uses an in-memory latch system for synchronous
 * command-response within the agent loop. Auto-dispatches commands via WebSocket on creation.
 */
@Service
public class DeviceCommandService {

	private static final Logger log = LoggerFactory.getLogger(DeviceCommandService.class);

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

	private static final Duration COMMAND_TTL = Duration.ofSeconds(30);

	private final DeviceCommandRepository commandRepository;

	private final DeviceCommandDispatcher dispatcher;

	/** In-memory latches for awaiting command results. Key = commandId. */
	private final ConcurrentHashMap<String, CommandResult> resultMap = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, CountDownLatch> latchMap = new ConcurrentHashMap<>();

	public DeviceCommandService(DeviceCommandRepository commandRepository, DeviceCommandDispatcher dispatcher) {
		this.commandRepository = commandRepository;
		this.dispatcher = dispatcher;
	}

	/** Create a command, persist it, and dispatch to the device via WebSocket. */
	public DeviceCommand createCommand(String userId, String deviceId, String sessionId, CommandType commandType,
			Map<String, Object> payload, int tier) {
		DeviceCommand cmd = new DeviceCommand();
		cmd.setId(UUID.randomUUID().toString());
		cmd.setUserId(userId);
		cmd.setDeviceId(deviceId);
		cmd.setSessionId(sessionId);
		cmd.setCommandType(commandType);
		cmd.setPayload(payload);
		cmd.setTier(tier);
		cmd.setState(CommandState.QUEUED);
		cmd.setCreatedDate(Timestamp.now());
		cmd.setExpiresAt(Instant.now().plus(COMMAND_TTL));

		// Set spark context if available
		SparkContext ctx = SparkContext.get();
		if (ctx != null) {
			cmd.setSparkId(ctx.getSparkId());
		}

		commandRepository.save(cmd, cmd.getId());
		log.info("Command created: {} ({}) for device {}", cmd.getId(), commandType, deviceId);

		// Prepare latch for synchronous wait
		latchMap.put(cmd.getId(), new CountDownLatch(1));

		// Dispatch to device via WebSocket
		dispatch(cmd);

		return cmd;
	}

	/**
	 * Wait for a command result. Blocks until the device reports back or timeout.
	 * @return the result text, or a timeout message
	 */
	public String awaitResult(String commandId, Duration timeout) {
		CountDownLatch latch = latchMap.get(commandId);
		if (latch == null) {
			return "Command not found: " + commandId;
		}

		try {
			boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!completed) {
				// Mark as expired
				commandRepository.findById(commandId).ifPresent(cmd -> {
					cmd.setState(CommandState.EXPIRED);
					commandRepository.save(cmd, cmd.getId());
				});
				cleanup(commandId);
				return "Command timed out — device did not respond within " + timeout.getSeconds() + " seconds.";
			}

			CommandResult result = resultMap.get(commandId);
			cleanup(commandId);
			if (result != null && result.success) {
				return result.message;
			}
			else if (result != null) {
				return "Command failed: " + result.message;
			}
			return "Command completed with no result.";
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			cleanup(commandId);
			return "Command interrupted.";
		}
	}

	/** Convenience method: await with default timeout. */
	public String awaitResult(String commandId) {
		return awaitResult(commandId, DEFAULT_TIMEOUT);
	}

	/** Called when a device reports a command result (from WebSocket handler). */
	public void reportResult(String commandId, boolean success, String message, Map<String, Object> resultData) {
		Optional<DeviceCommand> opt = commandRepository.findById(commandId);
		if (opt.isEmpty()) {
			log.warn("Result reported for unknown command: {}", commandId);
			return;
		}

		DeviceCommand cmd = opt.get();
		cmd.setState(success ? CommandState.COMPLETED : CommandState.FAILED);
		cmd.setResult(resultData);
		cmd.setCompletedAt(Instant.now());
		commandRepository.save(cmd, cmd.getId());

		// Release the latch
		resultMap.put(commandId, new CommandResult(success, message));
		CountDownLatch latch = latchMap.get(commandId);
		if (latch != null) {
			latch.countDown();
		}

		log.info("Command {} {}: {}", commandId, success ? "completed" : "failed", message);
	}

	private void dispatch(DeviceCommand cmd) {
		Map<String, Object> message = new HashMap<>();
		message.put("type", "command");
		message.put("commandId", cmd.getId());
		message.put("commandType", cmd.getCommandType().name());
		message.put("payload", cmd.getPayload());

		dispatcher.dispatch(cmd.getUserId(), cmd.getDeviceId(), message);

		cmd.setState(CommandState.SENT);
		cmd.setSentAt(Instant.now());
		commandRepository.save(cmd, cmd.getId());
	}

	private void cleanup(String commandId) {
		latchMap.remove(commandId);
		resultMap.remove(commandId);
	}

	/** Internal result holder. */
	private record CommandResult(boolean success, String message) {
	}

}
