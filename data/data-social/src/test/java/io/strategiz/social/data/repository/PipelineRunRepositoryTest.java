package io.strategiz.social.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineRunRepositoryTest {

	private static final String USER_ID = "user-1";

	private static final String SPARK_ID = "spark-1";

	@Mock
	private Firestore firestore;

	@Mock
	private FirestoreAuditingHandler auditingHandler;

	@Mock
	private CollectionReference pipelineRunsCol;

	@Mock
	private Query query;

	@Mock
	private Query activeQuery;

	@Mock
	private ApiFuture<QuerySnapshot> queryFuture;

	@Mock
	private QuerySnapshot querySnapshot;

	@Mock
	private QueryDocumentSnapshot doc1;

	@Mock
	private QueryDocumentSnapshot doc2;

	private PipelineRunRepository repository;

	@BeforeEach
	void setUp() {
		when(firestore.collection("pipeline_runs")).thenReturn(pipelineRunsCol);
		repository = new PipelineRunRepository(firestore, auditingHandler);
	}

	@Test
	void findBySparkId_returnsPresentWhenFound() throws Exception {
		PipelineRun run = new PipelineRun();
		run.setId("run-1");
		run.setSparkId(SPARK_ID);
		run.setUserId(USER_ID);
		run.setIsActive(true);

		when(pipelineRunsCol.whereEqualTo(eq("sparkId"), eq(SPARK_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(activeQuery);
		when(activeQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1));
		when(doc1.toObject(PipelineRun.class)).thenReturn(run);
		when(doc1.getId()).thenReturn("run-1");

		Optional<PipelineRun> result = repository.findBySparkId(SPARK_ID);

		assertTrue(result.isPresent());
		assertEquals("run-1", result.get().getId());
	}

	@Test
	void findBySparkId_returnsEmptyWhenNotFound() throws Exception {
		when(pipelineRunsCol.whereEqualTo(eq("sparkId"), eq(SPARK_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(activeQuery);
		when(activeQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of());

		Optional<PipelineRun> result = repository.findBySparkId(SPARK_ID);

		assertFalse(result.isPresent());
	}

	@Test
	void findByUserId_returnsAllRunsForUser() throws Exception {
		PipelineRun run1 = new PipelineRun();
		run1.setId("run-1");
		run1.setUserId(USER_ID);
		run1.setIsActive(true);

		PipelineRun run2 = new PipelineRun();
		run2.setId("run-2");
		run2.setUserId(USER_ID);
		run2.setIsActive(true);

		when(pipelineRunsCol.whereEqualTo(eq("userId"), eq(USER_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(activeQuery);
		when(activeQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));
		when(doc1.toObject(PipelineRun.class)).thenReturn(run1);
		when(doc1.getId()).thenReturn("run-1");
		when(doc2.toObject(PipelineRun.class)).thenReturn(run2);
		when(doc2.getId()).thenReturn("run-2");

		List<PipelineRun> result = repository.findByUserId(USER_ID);

		assertEquals(2, result.size());
	}

	@Test
	void findByStatus_returnsMatchingRuns() throws Exception {
		PipelineRun run = new PipelineRun();
		run.setId("run-1");
		run.setStatus(PipelineStatus.EXECUTING);
		run.setIsActive(true);

		when(pipelineRunsCol.whereEqualTo(eq("status"), eq("EXECUTING"))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(activeQuery);
		when(activeQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1));
		when(doc1.toObject(PipelineRun.class)).thenReturn(run);
		when(doc1.getId()).thenReturn("run-1");

		List<PipelineRun> result = repository.findByStatus(PipelineStatus.EXECUTING);

		assertEquals(1, result.size());
		assertEquals(PipelineStatus.EXECUTING, result.get(0).getStatus());
	}

	@Test
	void findByUserIdAndStatus_filtersByStatus() throws Exception {
		PipelineRun executing = new PipelineRun();
		executing.setId("run-1");
		executing.setUserId(USER_ID);
		executing.setStatus(PipelineStatus.EXECUTING);
		executing.setIsActive(true);

		PipelineRun completed = new PipelineRun();
		completed.setId("run-2");
		completed.setUserId(USER_ID);
		completed.setStatus(PipelineStatus.COMPLETED);
		completed.setIsActive(true);

		when(pipelineRunsCol.whereEqualTo(eq("userId"), eq(USER_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(activeQuery);
		when(activeQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));
		when(doc1.toObject(PipelineRun.class)).thenReturn(executing);
		when(doc1.getId()).thenReturn("run-1");
		when(doc2.toObject(PipelineRun.class)).thenReturn(completed);
		when(doc2.getId()).thenReturn("run-2");

		List<PipelineRun> result = repository.findByUserIdAndStatus(USER_ID, PipelineStatus.EXECUTING);

		assertEquals(1, result.size());
		assertEquals("run-1", result.get(0).getId());
		assertEquals(PipelineStatus.EXECUTING, result.get(0).getStatus());
	}

}
