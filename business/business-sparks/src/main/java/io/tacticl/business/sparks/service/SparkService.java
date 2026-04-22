package io.tacticl.business.sparks.service;

import io.tacticl.data.sparks.entity.*;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class SparkService {

    private final SparkRepository sparkRepository;

    public SparkService(SparkRepository sparkRepository) {
        this.sparkRepository = sparkRepository;
    }

    public Spark create(String userId, String input) {
        return sparkRepository.save(Spark.create(userId, input));
    }

    // Exposed so callers that enrich a spark post-create (e.g. Telegram group initiator
    // stamping initiatorSource/initiatorUserId/projectId) can persist the mutation
    // without a second round-trip through a specialized updater.
    public Spark save(Spark spark) {
        return sparkRepository.save(spark);
    }

    public Spark classify(String sparkId, String userId, SparkType type) {
        Spark spark = sparkRepository.findByIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Spark not found: " + sparkId));
        spark.classify(type);
        return sparkRepository.save(spark);
    }

    public Spark markExecuting(String sparkId, String userId, SparkRoute route, String deviceId) {
        Spark spark = sparkRepository.findByIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Spark not found: " + sparkId));
        spark.markExecuting(route, deviceId);
        return sparkRepository.save(spark);
    }

    public Spark markCompleted(String sparkId, String userId, int tokenCost, String modelUsed) {
        Spark spark = sparkRepository.findByIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Spark not found: " + sparkId));
        spark.markCompleted(tokenCost, modelUsed);
        return sparkRepository.save(spark);
    }

    public Spark markFailed(String sparkId, String userId) {
        Spark spark = sparkRepository.findByIdAndUserId(sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Spark not found: " + sparkId));
        spark.markFailed();
        return sparkRepository.save(spark);
    }

    public void cancel(String sparkId, String userId) {
        sparkRepository.findByIdAndUserId(sparkId, userId).ifPresent(spark -> {
            spark.cancel();
            sparkRepository.save(spark);
        });
    }

    public Optional<Spark> get(String userId, String sparkId) {
        return sparkRepository.findByIdAndUserId(sparkId, userId);
    }

    public Page<Spark> list(String userId, int page, int size) {
        return sparkRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }
}
