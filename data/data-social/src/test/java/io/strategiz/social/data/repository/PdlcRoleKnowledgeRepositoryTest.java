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
import io.strategiz.social.data.entity.PdlcRoleKnowledge;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdlcRoleKnowledgeRepositoryTest {

	@Mock
	private Firestore firestore;

	@Mock
	private FirestoreAuditingHandler auditingHandler;

	@Mock
	private CollectionReference knowledgeCol;

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

	@Mock
	private QueryDocumentSnapshot doc3;

	private PdlcRoleKnowledgeRepository repository;

	@BeforeEach
	void setUp() {
		when(firestore.collection("pdlc_role_knowledge")).thenReturn(knowledgeCol);
		repository = new PdlcRoleKnowledgeRepository(firestore, auditingHandler);
	}

	@Test
	void findByRole_returnsAllEntriesForRole() throws Exception {
		when(knowledgeCol.whereEqualTo(eq("role"), eq("IMPLEMENTER"))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(isActiveQuery);
		when(isActiveQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);

		PdlcRoleKnowledge k1 = new PdlcRoleKnowledge();
		k1.setId("k-1");
		k1.setRole(PdlcRole.IMPLEMENTER);
		k1.setCategory("BEST_PRACTICE");
		k1.setIsActive(true);

		PdlcRoleKnowledge k2 = new PdlcRoleKnowledge();
		k2.setId("k-2");
		k2.setRole(PdlcRole.IMPLEMENTER);
		k2.setCategory("ANTI_PATTERN");
		k2.setIsActive(true);

		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2));
		when(doc1.toObject(PdlcRoleKnowledge.class)).thenReturn(k1);
		when(doc1.getId()).thenReturn("k-1");
		when(doc2.toObject(PdlcRoleKnowledge.class)).thenReturn(k2);
		when(doc2.getId()).thenReturn("k-2");

		List<PdlcRoleKnowledge> result = repository.findByRole(PdlcRole.IMPLEMENTER);

		assertEquals(2, result.size());
		assertEquals("k-1", result.get(0).getId());
		assertEquals("k-2", result.get(1).getId());
	}

	@Test
	void findByRole_returnsEmptyListWhenNoneFound() throws Exception {
		when(knowledgeCol.whereEqualTo(eq("role"), eq("TESTER"))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(isActiveQuery);
		when(isActiveQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);
		when(querySnapshot.getDocuments()).thenReturn(List.of());

		List<PdlcRoleKnowledge> result = repository.findByRole(PdlcRole.TESTER);

		assertTrue(result.isEmpty());
	}

	@Test
	void findByRoleAndCategory_filtersToMatchingCategory() throws Exception {
		when(knowledgeCol.whereEqualTo(eq("role"), eq("REVIEWER"))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(isActiveQuery);
		when(isActiveQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);

		PdlcRoleKnowledge bestPractice = new PdlcRoleKnowledge();
		bestPractice.setId("k-bp");
		bestPractice.setRole(PdlcRole.REVIEWER);
		bestPractice.setCategory("BEST_PRACTICE");
		bestPractice.setIsActive(true);

		PdlcRoleKnowledge antiPattern = new PdlcRoleKnowledge();
		antiPattern.setId("k-ap");
		antiPattern.setRole(PdlcRole.REVIEWER);
		antiPattern.setCategory("ANTI_PATTERN");
		antiPattern.setIsActive(true);

		PdlcRoleKnowledge retro = new PdlcRoleKnowledge();
		retro.setId("k-retro");
		retro.setRole(PdlcRole.REVIEWER);
		retro.setCategory("RETRO_LEARNING");
		retro.setIsActive(true);

		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1, doc2, doc3));
		when(doc1.toObject(PdlcRoleKnowledge.class)).thenReturn(bestPractice);
		when(doc1.getId()).thenReturn("k-bp");
		when(doc2.toObject(PdlcRoleKnowledge.class)).thenReturn(antiPattern);
		when(doc2.getId()).thenReturn("k-ap");
		when(doc3.toObject(PdlcRoleKnowledge.class)).thenReturn(retro);
		when(doc3.getId()).thenReturn("k-retro");

		List<PdlcRoleKnowledge> result = repository.findByRoleAndCategory(PdlcRole.REVIEWER, "BEST_PRACTICE");

		assertEquals(1, result.size());
		assertEquals("k-bp", result.get(0).getId());
		assertEquals("BEST_PRACTICE", result.get(0).getCategory());
	}

	@Test
	void findByRoleAndCategory_returnsEmptyWhenCategoryNotFound() throws Exception {
		when(knowledgeCol.whereEqualTo(eq("role"), eq("PM"))).thenReturn(query);
		when(query.whereEqualTo(eq("isActive"), eq(true))).thenReturn(isActiveQuery);
		when(isActiveQuery.get()).thenReturn(queryFuture);
		when(queryFuture.get()).thenReturn(querySnapshot);

		PdlcRoleKnowledge k1 = new PdlcRoleKnowledge();
		k1.setId("k-1");
		k1.setRole(PdlcRole.PM);
		k1.setCategory("BEST_PRACTICE");
		k1.setIsActive(true);

		when(querySnapshot.getDocuments()).thenReturn(List.of(doc1));
		when(doc1.toObject(PdlcRoleKnowledge.class)).thenReturn(k1);
		when(doc1.getId()).thenReturn("k-1");

		List<PdlcRoleKnowledge> result = repository.findByRoleAndCategory(PdlcRole.PM, "EXAMPLE");

		assertTrue(result.isEmpty());
	}

}
