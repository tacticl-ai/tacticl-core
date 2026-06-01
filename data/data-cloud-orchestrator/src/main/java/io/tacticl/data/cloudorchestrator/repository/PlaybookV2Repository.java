package io.tacticl.data.cloudorchestrator.repository;

import io.tacticl.data.cloudorchestrator.entity.PlaybookV2;
import io.tacticl.data.sparks.entity.SparkType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PlaybookV2Repository extends MongoRepository<PlaybookV2, String> {
    List<PlaybookV2> findBySparkTypesContaining(SparkType sparkType);
    List<PlaybookV2> findByActive(boolean active);
}
