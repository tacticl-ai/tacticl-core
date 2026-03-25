package io.strategiz.social.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineArtifactRepositoryTest {

	private static final String RUN_ID = "run-abc-123";

	@Mock
	private Firestore firestore;

	@Mock
	private FirestoreAuditingHandler auditingHandler;

	@Mock
	private CollectionReference artifactsCol;

	@Mock
	private Query query;

	@Mock
	private Query isActiveQuery;

	@Mock
	private ApiFuture<QuerySnapshot> queryFuture;

	@Mock
	private QuerySnapshot querySnapshot;

	@Mock
	private QueryDocumentSnapshot doc1;

	@Mock
	private QueryDocumentSnapshot doc2;

	private PipelineArtifactRepository repository;

	@BeforeEach
	void setUp() {
		when(firestore.collection("pipeline_artifacts")).thenReturn(artifactsCol);
		repository = new PipelineArtifactRepository(firestore, auditingHandler);
	}

	@Test
	void findByPipelineRunId_returnsAllArtifactsForRun() throws Exception {
		when(artifactsCol.whereEqualTo(eq("pipelineRunId"), eq(RUN_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(isActiveQuery);
		when(isActiveQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);

		PipelineArtifact artifact1 = new PipelineArtifact();
		artifact1.setId("art-1");
		artifact1.setPipelineRunId(RUN_ID);
		artifact1.setRole(PdlcRole.PM);
		artifact1.setIsActive(true);

		PipelineArtifact artifact2 = new PipelineArtifact();
		artifact2.setId("art-2");
		artifact2.setPipelineRunId(RUN_ID);
		artifact2.setRole(PdlcRole.ARCHITECT);
		artifact2.setIsActive(true);

		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));
		when(doc1.toObject(PipelineArtifact.class)).thenReturn(artifact1);
		when(doc1.getId()).thenReturn("art-1");
		when(doc2.toObject(PipelineArtifact.class)).thenReturn(artifact2);
		when(doc2.getId()).thenReturn("art-2");

		List<PipelineArtifact> result = repository.findByPipelineRunId(RUN_ID);

		assertEquals(2, result.size());
		assertEquals("art-1", result.get(0).getId());
		assertEquals("art-2", result.get(1).getId());
	}

	@Test
	void findByPipelineRunId_returnsEmptyListWhenNoneFound() throws Exception {
		when(artifactsCol.whereEqualTo(eq("pipelineRunId"), eq(RUN_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(isActiveQuery);
		when(isActiveQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of());

		List<PipelineArtifact> result = repository.findByPipelineRunId(RUN_ID);

		assertTrue(result.isEmpty());
	}

	@Test
	void findByPipelineRunIdAndRole_returnsMatchingArtifact() throws Exception {
		when(artifactsCol.whereEqualTo(eq("pipelineRunId"), eq(RUN_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(isActiveQuery);
		when(isActiveQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);

		PipelineArtifact pmArtifact = new PipelineArtifact();
		pmArtifact.setId("art-pm");
		pmArtifact.setPipelineRunId(RUN_ID);
		pmArtifact.setRole(PdlcRole.PM);
		pmArtifact.setIsActive(true);

		PipelineArtifact archArtifact = new PipelineArtifact();
		archArtifact.setId("art-arch");
		archArtifact.setPipelineRunId(RUN_ID);
		archArtifact.setRole(PdlcRole.ARCHITECT);
		archArtifact.setIsActive(true);

		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));
		when(doc1.toObject(PipelineArtifact.class)).thenReturn(pmArtifact);
		when(doc1.getId()).thenReturn("art-pm");
		when(doc2.toObject(PipelineArtifact.class)).thenReturn(archArtifact);
		when(doc2.getId()).thenReturn("art-arch");

		Optional<PipelineArtifact> result = repository.findByPipelineRunIdAndRole(RUN_ID, PdlcRole.ARCHITECT);

		assertTrue(result.isPresent());
		assertEquals("art-arch", result.get().getId());
		assertEquals(PdlcRole.ARCHITECT, result.get().getRole());
	}

	@Test
	void findByPipelineRunIdAndRole_returnsEmptyWhenRoleNotFound() throws Exception {
		when(artifactsCol.whereEqualTo(eq("pipelineRunId"), eq(RUN_ID))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(isActiveQuery);
		when(isActiveQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);

		PipelineArtifact pmArtifact = new PipelineArtifact();
		pmArtifact.setId("art-pm");
		pmArtifact.setPipelineRunId(RUN_ID);
		pmArtifact.setRole(PdlcRole.PM);
		pmArtifact.setIsActive(true);

		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1));
		when(doc1.toObject(PipelineArtifact.class)).thenReturn(pmArtifact);
		when(doc1.getId()).thenReturn("art-pm");

		Optional<PipelineArtifact> result = repository.findByPipelineRunIdAndRole(RUN_ID, PdlcRole.TESTER);

		assertTrue(result.isEmpty());
	}

}
