package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PdlcRoleKnowledge;
import io.strategiz.social.data.repository.PdlcRoleKnowledgeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

	@Mock
	private PdlcRoleKnowledgeRepository knowledgeRepository;

	private KnowledgeBaseService knowledgeBaseService;

	@BeforeEach
	void setUp() {
		knowledgeBaseService = new KnowledgeBaseService(knowledgeRepository);
	}

	@Test
	void addKnowledge_savesEntityAndReturnsId() {
		PdlcRoleKnowledge saved = new PdlcRoleKnowledge();
		saved.setId("knowledge-123");
		when(knowledgeRepository.save(any(PdlcRoleKnowledge.class))).thenReturn(saved);

		String knowledgeId = knowledgeBaseService.addKnowledge(
			PdlcRole.PM, "BEST_PRACTICE", "Always define acceptance criteria.", "bootstrap");

		assertNotNull(knowledgeId);
		assertFalse(knowledgeId.isBlank());

		ArgumentCaptor<PdlcRoleKnowledge> captor = ArgumentCaptor.forClass(PdlcRoleKnowledge.class);
		verify(knowledgeRepository).save(captor.capture());

		PdlcRoleKnowledge captured = captor.getValue();
		assertEquals(PdlcRole.PM, captured.getRole());
		assertEquals("BEST_PRACTICE", captured.getCategory());
		assertEquals("Always define acceptance criteria.", captured.getContent());
		assertEquals("bootstrap", captured.getSource());
		assertEquals(1.0, captured.getRelevanceScore());
		assertNotNull(captured.getEmbedding());
		assertTrue(captured.getEmbedding().isEmpty());
		assertNotNull(captured.getCreatedAt());
	}

	@Test
	void queryKnowledge_returnsTopKResultsSortedByRelevance() {
		PdlcRoleKnowledge lowRelevance = new PdlcRoleKnowledge();
		lowRelevance.setId("k1");
		lowRelevance.setContent("Always write tests");
		lowRelevance.setRelevanceScore(0.5);

		PdlcRoleKnowledge highRelevance = new PdlcRoleKnowledge();
		highRelevance.setId("k2");
		highRelevance.setContent("Handle API errors gracefully");
		highRelevance.setRelevanceScore(2.0);

		PdlcRoleKnowledge mediumRelevance = new PdlcRoleKnowledge();
		mediumRelevance.setId("k3");
		mediumRelevance.setContent("Follow existing code patterns in the codebase");
		mediumRelevance.setRelevanceScore(1.5);

		when(knowledgeRepository.findByRole(PdlcRole.IMPLEMENTER))
			.thenReturn(List.of(lowRelevance, highRelevance, mediumRelevance));

		List<PdlcRoleKnowledge> results =
			knowledgeBaseService.queryKnowledge(PdlcRole.IMPLEMENTER, "API error handling", 2);

		assertEquals(2, results.size());
		// highRelevance has highest relevanceScore and keyword matches "API" and "errors"/"error"
		assertEquals("k2", results.get(0).getId());
	}

	@Test
	void queryKnowledge_returnsEmptyListWhenNoKnowledgeExists() {
		when(knowledgeRepository.findByRole(PdlcRole.DESIGNER)).thenReturn(List.of());

		List<PdlcRoleKnowledge> results =
			knowledgeBaseService.queryKnowledge(PdlcRole.DESIGNER, "some context", 5);

		assertTrue(results.isEmpty());
	}

	@Test
	void updateRelevance_adjustsScoreCorrectly() {
		PdlcRoleKnowledge entry = new PdlcRoleKnowledge();
		entry.setId("k1");
		entry.setRelevanceScore(5.0);
		when(knowledgeRepository.findById("k1")).thenReturn(Optional.of(entry));

		knowledgeBaseService.updateRelevance("k1", 2.0);

		ArgumentCaptor<PdlcRoleKnowledge> captor = ArgumentCaptor.forClass(PdlcRoleKnowledge.class);
		verify(knowledgeRepository).save(captor.capture());
		assertEquals(7.0, captor.getValue().getRelevanceScore(), 0.001);
	}

	@Test
	void updateRelevance_clampsAtMaximum() {
		PdlcRoleKnowledge entry = new PdlcRoleKnowledge();
		entry.setId("k1");
		entry.setRelevanceScore(9.5);
		when(knowledgeRepository.findById("k1")).thenReturn(Optional.of(entry));

		knowledgeBaseService.updateRelevance("k1", 5.0);

		ArgumentCaptor<PdlcRoleKnowledge> captor = ArgumentCaptor.forClass(PdlcRoleKnowledge.class);
		verify(knowledgeRepository).save(captor.capture());
		assertEquals(10.0, captor.getValue().getRelevanceScore(), 0.001);
	}

	@Test
	void updateRelevance_clampsAtMinimum() {
		PdlcRoleKnowledge entry = new PdlcRoleKnowledge();
		entry.setId("k1");
		entry.setRelevanceScore(0.5);
		when(knowledgeRepository.findById("k1")).thenReturn(Optional.of(entry));

		knowledgeBaseService.updateRelevance("k1", -5.0);

		ArgumentCaptor<PdlcRoleKnowledge> captor = ArgumentCaptor.forClass(PdlcRoleKnowledge.class);
		verify(knowledgeRepository).save(captor.capture());
		assertEquals(0.0, captor.getValue().getRelevanceScore(), 0.001);
	}

	@Test
	void buildKnowledgeContext_returnsFormattedText() {
		PdlcRoleKnowledge k1 = new PdlcRoleKnowledge();
		k1.setContent("Always define measurable acceptance criteria.");
		k1.setRelevanceScore(1.0);

		PdlcRoleKnowledge k2 = new PdlcRoleKnowledge();
		k2.setContent("Scope boundaries explicitly — document what is NOT in scope.");
		k2.setRelevanceScore(1.0);

		when(knowledgeRepository.findByRole(PdlcRole.PM)).thenReturn(List.of(k1, k2));

		String context = knowledgeBaseService.buildKnowledgeContext(PdlcRole.PM, "define project scope");

		assertTrue(context.contains("## Role Knowledge"));
		assertTrue(context.contains("- Always define measurable acceptance criteria."));
		assertTrue(context.contains("- Scope boundaries explicitly"));
	}

	@Test
	void buildKnowledgeContext_returnsEmptyStringWhenNoKnowledge() {
		when(knowledgeRepository.findByRole(PdlcRole.DEVOPS)).thenReturn(List.of());

		String context = knowledgeBaseService.buildKnowledgeContext(PdlcRole.DEVOPS, "deploy to production");

		assertEquals("", context);
	}

	@Test
	void bootstrapKnowledge_createsEntriesForAllRolesWhenEmpty() {
		// Return empty list so bootstrap proceeds
		when(knowledgeRepository.findByRole(PdlcRole.PM)).thenReturn(List.of());
		when(knowledgeRepository.save(any(PdlcRoleKnowledge.class)))
			.thenAnswer(inv -> inv.getArgument(0));

		knowledgeBaseService.bootstrapKnowledge();

		// 12 roles × 3 entries each = 36 saves
		verify(knowledgeRepository, times(36)).save(any(PdlcRoleKnowledge.class));
	}

	@Test
	void bootstrapKnowledge_skipsWhenKnowledgeAlreadyExists() {
		PdlcRoleKnowledge existing = new PdlcRoleKnowledge();
		existing.setId("existing-1");
		when(knowledgeRepository.findByRole(PdlcRole.PM)).thenReturn(List.of(existing));

		knowledgeBaseService.bootstrapKnowledge();

		verify(knowledgeRepository, never()).save(any(PdlcRoleKnowledge.class));
	}

}
