package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.CheckpointDecision;
import io.strategiz.social.data.entity.Spark;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dispatches spark lifecycle commands to devices via WebSocket. Handles spark_dispatch,
 * spark_cancel, and checkpoint_decision messages.
 */
@Service
public class SparkDispatchService {

	private static final Logger log = LoggerFactory.getLogger(SparkDispatchService.class);

	private final DeviceCommandDispatcher dispatcher;

	public SparkDispatchService(DeviceCommandDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	/**
	 * Dispatch a spark to a device for execution. Sends the full spark payload so the device can
	 * decompose it into tactics.
	 * @return true if the device was connected and dispatch was attempted
	 */
	public boolean dispatchSpark(Spark spark) {
		String deviceId = spark.getDeviceId();
		if (deviceId == null) {
			log.warn("[SPARK] Cannot dispatch spark={}: no device assigned", spark.getId());
			return false;
		}

		if (!dispatcher.isDeviceConnected(deviceId)) {
			log.warn("[SPARK] Cannot dispatch spark={}: device {} not connected", spark.getId(), deviceId);
			return false;
		}

		Map<String, Object> message = new HashMap<>();
		message.put("type", "spark_dispatch");
		message.put("sparkId", spark.getId());
		message.put("title", spark.getTitle());
		message.put("description", spark.getDescription());
		message.put("sparkType", spark.getType());
		message.put("priority", spark.getPriority().name());
		message.put("checkpointPolicy", spark.getCheckpointPolicy().name());
		message.put("repoAccess", spark.getRepoAccess());

		dispatcher.dispatch(spark.getUserId(), deviceId, message);
		log.info("[SPARK] Dispatched spark={} to device={}", spark.getId(), deviceId);
		return true;
	}

	/**
	 * Send a cancel command to the device running a spark.
	 * @return true if the device was connected and cancel was attempted
	 */
	public boolean cancelSparkOnDevice(Spark spark) {
		String deviceId = spark.getDeviceId();
		if (deviceId == null || !dispatcher.isDeviceConnected(deviceId)) {
			return false;
		}

		Map<String, Object> message = new HashMap<>();
		message.put("type", "spark_cancel");
		message.put("sparkId", spark.getId());

		dispatcher.dispatch(spark.getUserId(), deviceId, message);
		log.info("[SPARK] Sent cancel for spark={} to device={}", spark.getId(), deviceId);
		return true;
	}

	/**
	 * Relay a checkpoint decision to the device so it can resume or abort execution.
	 * @return true if the device was connected and relay was attempted
	 */
	public boolean relayCheckpointDecision(Spark spark, String checkpointId, CheckpointDecision decision,
			String feedback) {
		String deviceId = spark.getDeviceId();
		if (deviceId == null || !dispatcher.isDeviceConnected(deviceId)) {
			return false;
		}

		Map<String, Object> message = new HashMap<>();
		message.put("type", "checkpoint_decision");
		message.put("sparkId", spark.getId());
		message.put("checkpointId", checkpointId);
		message.put("decision", decision.name());
		if (feedback != null) {
			message.put("feedback", feedback);
		}

		dispatcher.dispatch(spark.getUserId(), deviceId, message);
		log.info("[SPARK] Relayed checkpoint decision {} for checkpoint={} to device={}", decision, checkpointId,
				deviceId);
		return true;
	}

}
