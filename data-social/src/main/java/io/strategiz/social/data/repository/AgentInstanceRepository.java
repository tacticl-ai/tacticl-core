package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.AgentInstance;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for agent_instances Firestore collection. */
@Repository
public class AgentInstanceRepository extends FirestoreRepository<AgentInstance> {

	public AgentInstanceRepository(Firestore firestore) {
		super(firestore, AgentInstance.class, "agent_instances");
	}

	/** Find agent instance for a task. */
	public List<AgentInstance> findByTaskId(String taskId) {
		return findByField("taskId", taskId);
	}

	/** Find all agent instances for an ask. */
	public List<AgentInstance> findByAskId(String askId) {
		return findByField("askId", askId);
	}

}
