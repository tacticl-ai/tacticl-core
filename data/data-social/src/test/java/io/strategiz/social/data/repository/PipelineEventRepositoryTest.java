package io.strategiz.social.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import io.cidadel.data.base.audit.FirestoreAuditingHandler;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineEventRepositoryTest {

	private static final String USER_ID = "user-1";

	private static final String PIPELINE_RUN_ID = "run-1";

	@Mock
	private Firestore firestore;

	@Mock
	private FirestoreAuditingHandler auditingHandler;

	@Mock
	private CollectionReference pipelineEventsCol;

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

	private PipelineEventRepository repository;

	@BeforeEach
	void setUp() {
		when(firestore.collection("pipeline_events")).thenReturn(pipelineEventsCol);
		repository = new PipelineEventRepository(firestore, auditingHandler);
	}

	@Test
	void findByPipelineRunId_returnsAllEventsForRun() throws Exception {
		PipelineEvent event1 = new PipelineEvent();
		event1.setId("evt-1");
		event1.setPipelineRunId(PIPELINE_RUN_ID);
		event1.setEventType(PipelineEventType.PIPELINE_STARTED);
		event1.setIsActive(true);

		PipelineEvent event2 = new PipelineEvent();
		event2.setId("evt-2");
		event2.setPipelineRunId(PIPELINE_RUN_ID);
		event2.setEventType(PipelineEventType.ROLE_STARTED);
		event2.setRole(PdlcRole.PM);
		event2.setIsActive(true);

		when(pipelineEventsCol.whereEqualTo(eq("pipelineRunId"), eq(PIPELINE_RUN_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(activeQuery);
		when(activeQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));
		when(doc1.toObject(PipelineEvent.class)).thenReturn(event1);
		when(doc1.getId()).thenReturn("evt-1");
		when(doc2.toObject(PipelineEvent.class)).thenReturn(event2);
		when(doc2.getId()).thenReturn("evt-2");

		List<PipelineEvent> result = repository.findByPipelineRunId(PIPELINE_RUN_ID);

		assertEquals(2, result.size());
	}

	@Test
	void findByPipelineRunIdAndEventType_filtersToMatchingType() throws Exception {
		PipelineEvent started = new PipelineEvent();
		started.setId("evt-1");
		started.setPipelineRunId(PIPELINE_RUN_ID);
		started.setEventType(PipelineEventType.PIPELINE_STARTED);
		started.setIsActive(true);

		PipelineEvent roleStarted = new PipelineEvent();
		roleStarted.setId("evt-2");
		roleStarted.setPipelineRunId(PIPELINE_RUN_ID);
		roleStarted.setEventType(PipelineEventType.ROLE_STARTED);
		roleStarted.setIsActive(true);

		when(pipelineEventsCol.whereEqualTo(eq("pipelineRunId"), eq(PIPELINE_RUN_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(activeQuery);
		when(activeQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));
		when(doc1.toObject(PipelineEvent.class)).thenReturn(started);
		when(doc1.getId()).thenReturn("evt-1");
		when(doc2.toObject(PipelineEvent.class)).thenReturn(roleStarted);
		when(doc2.getId()).thenReturn("evt-2");

		List<PipelineEvent> result = repository.findByPipelineRunIdAndEventType(PIPELINE_RUN_ID,
				PipelineEventType.ROLE_STARTED);

		assertEquals(1, result.size());
		assertEquals("evt-2", result.get(0).getId());
		assertEquals(PipelineEventType.ROLE_STARTED, result.get(0).getEventType());
	}

	@Test
	void findByUserId_returnsAllEventsForUser() throws Exception {
		PipelineEvent event = new PipelineEvent();
		event.setId("evt-1");
		event.setUserId(USER_ID);
		event.setEventType(PipelineEventType.PIPELINE_COMPLETED);
		event.setIsActive(true);

		when(pipelineEventsCol.whereEqualTo(eq("userId"), eq(USER_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(activeQuery);
		when(activeQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1));
		when(doc1.toObject(PipelineEvent.class)).thenReturn(event);
		when(doc1.getId()).thenReturn("evt-1");

		List<PipelineEvent> result = repository.findByUserId(USER_ID);

		assertEquals(1, result.size());
		assertEquals("evt-1", result.get(0).getId());
	}

	@Test
	void findByPipelineRunId_returnsEmptyListWhenNoEvents() throws Exception {
		when(pipelineEventsCol.whereEqualTo(eq("pipelineRunId"), eq(PIPELINE_RUN_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(activeQuery);
		when(activeQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of());

		List<PipelineEvent> result = repository.findByPipelineRunId(PIPELINE_RUN_ID);

		assertEquals(0, result.size());
	}

}
