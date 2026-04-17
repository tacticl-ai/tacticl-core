package io.tacticl.data.pipeline.repository;

import io.tacticl.data.pipeline.entity.AgentKnowledge;
import io.tacticl.data.pipeline.entity.KnowledgeStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface AgentKnowledgeRepository extends MongoRepository<AgentKnowledge, String> {
    List<AgentKnowledge> findByProductAndStatusIn(String product, List<KnowledgeStatus> statuses);
}
