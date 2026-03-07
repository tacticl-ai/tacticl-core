package io.strategiz.social.application.job;

import io.strategiz.social.business.agent.service.SparkDispatchService;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.repository.SparkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that polls for SCHEDULED sparks whose nextRunAt has arrived, creates child
 * execution instances, routes and dispatches them, then updates the template's next run time.
 */
@Component
public class SparkSchedulerJob {

	private static final Logger log = LoggerFactory.getLogger(SparkSchedulerJob.class);

	private final SparkRepository sparkRepository;

	private final SparkService sparkService;

	private final SparkDispatchService sparkDispatchService;

	public SparkSchedulerJob(SparkRepository sparkRepository, SparkService sparkService,
			SparkDispatchService sparkDispatchService) {
		this.sparkRepository = sparkRepository;
		this.sparkService = sparkService;
		this.sparkDispatchService = sparkDispatchService;
	}

	/** Run every 60 seconds to check for scheduled sparks due for execution. */
	@Scheduled(fixedDelay = 60000)
	public void executeScheduledSparks() {
		List<Spark> dueSparks = sparkRepository.findScheduledDue(Instant.now());
		if (dueSparks.isEmpty()) {
			return;
		}

		log.info("[SCHEDULER] Found {} scheduled sparks due for execution", dueSparks.size());

		for (Spark templateSpark : dueSparks) {
			try {
				executeScheduledSpark(templateSpark);
			}
			catch (Exception e) {
				log.error("[SCHEDULER] Failed to execute scheduled spark {}: {}", templateSpark.getId(),
						e.getMessage(), e);
			}
		}
	}

	private void executeScheduledSpark(Spark templateSpark) {
		// Create a child spark for this execution (no schedule, so it runs once)
		Spark childSpark = sparkService.createSpark(templateSpark.getUserId(), templateSpark.getTitle(),
				templateSpark.getDescription(), templateSpark.getType(), templateSpark.getPriority(),
				templateSpark.getCheckpointPolicy(), templateSpark.getRepoAccess(), null);
		childSpark.setParentSparkId(templateSpark.getId());
		sparkRepository.save(childSpark, childSpark.getId());

		log.info("[SCHEDULER] Created child spark={} from template={}", childSpark.getId(), templateSpark.getId());

		// Route and dispatch the child spark
		Optional<DeviceRegistration> device = sparkService.routeSpark(childSpark.getId(),
				templateSpark.getUserId());
		if (device.isPresent()) {
			sparkDispatchService.dispatchSpark(childSpark);
		}
		else {
			log.warn("[SCHEDULER] No device available for child spark={}", childSpark.getId());
		}

		// Update the template spark's next run time
		Instant nextRunAt = sparkService.calculateNextRunAt(templateSpark.getSchedule());
		templateSpark.setNextRunAt(nextRunAt);
		sparkRepository.save(templateSpark, templateSpark.getId());

		log.info("[SCHEDULER] Updated template spark={} nextRunAt={}", templateSpark.getId(), nextRunAt);
	}

}
