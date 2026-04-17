package io.tacticl.business.sparks.service;

import io.tacticl.data.sparks.entity.*;
import io.tacticl.data.sparks.repository.CheckpointRepository;
import org.springframework.stereotype.Service;

@Service
public class CheckpointService {

    private final CheckpointRepository checkpointRepository;

    public CheckpointService(CheckpointRepository checkpointRepository) {
        this.checkpointRepository = checkpointRepository;
    }

    public Checkpoint create(String sparkId, String userId, CheckpointType type, String prompt) {
        return checkpointRepository.save(Checkpoint.create(sparkId, userId, type, prompt));
    }

    public Checkpoint resolve(String checkpointId, String sparkId, String userId,
                              CheckpointStatus decision, String instructions) {
        Checkpoint checkpoint = checkpointRepository
                .findByIdAndSparkIdAndUserId(checkpointId, sparkId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));
        checkpoint.resolve(decision, instructions);
        return checkpointRepository.save(checkpoint);
    }
}
