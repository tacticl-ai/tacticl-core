package io.strategiz.social.data.repository;

import com.google.cloud.firestore.Firestore;
import io.strategiz.social.data.entity.AgentTask;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for agent_tasks Firestore collection. */
@Repository
public class AgentTaskRepository extends FirestoreRepository<AgentTask> {

	public AgentTaskRepository(Firestore firestore) {
		super(firestore, AgentTask.class, "agent_tasks");
	}

	/** Find all tasks for an ask. */
	public List<AgentTask> findByAskId(String askId) {
		return findByField("askId", askId);
	}

}
