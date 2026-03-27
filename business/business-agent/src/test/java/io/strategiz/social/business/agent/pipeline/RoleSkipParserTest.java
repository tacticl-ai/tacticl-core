package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.strategiz.social.data.entity.PdlcRole;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleSkipParserTest {

	// --- No skip intent ---

	@Test
	void parse_nullInput_returnsEmptySet() {
		assertTrue(RoleSkipParser.parse(null).isEmpty());
	}

	@Test
	void parse_emptyString_returnsEmptySet() {
		assertTrue(RoleSkipParser.parse("").isEmpty());
	}

	@Test
	void parse_blankString_returnsEmptySet() {
		assertTrue(RoleSkipParser.parse("   ").isEmpty());
	}

	@Test
	void parse_commandWithNoSkipKeywords_returnsEmptySet() {
		assertTrue(RoleSkipParser.parse("Add a login feature with full test coverage").isEmpty());
	}

	// --- REVIEWER ---

	@Test
	void parse_skipReview_returnsReviewer() {
		assertEquals(Set.of(PdlcRole.REVIEWER), RoleSkipParser.parse("skip review please"));
	}

	@Test
	void parse_noReview_returnsReviewer() {
		assertEquals(Set.of(PdlcRole.REVIEWER), RoleSkipParser.parse("no review needed"));
	}

	@Test
	void parse_skipReviewCaseInsensitive_returnsReviewer() {
		assertEquals(Set.of(PdlcRole.REVIEWER), RoleSkipParser.parse("Skip Review"));
	}

	// --- TESTER ---

	@Test
	void parse_skipTests_returnsTester() {
		assertEquals(Set.of(PdlcRole.TESTER), RoleSkipParser.parse("skip tests"));
	}

	@Test
	void parse_noTesting_returnsTester() {
		assertEquals(Set.of(PdlcRole.TESTER), RoleSkipParser.parse("no testing required"));
	}

	@Test
	void parse_dontTest_returnsTester() {
		assertEquals(Set.of(PdlcRole.TESTER), RoleSkipParser.parse("don't test this"));
	}

	@Test
	void parse_dontTestContraction_returnsTester() {
		assertEquals(Set.of(PdlcRole.TESTER), RoleSkipParser.parse("don't test"));
	}

	// --- SECURITY_ANALYST ---

	@Test
	void parse_skipSecurity_returnsSecurityAnalyst() {
		assertEquals(Set.of(PdlcRole.SECURITY_ANALYST), RoleSkipParser.parse("skip security"));
	}

	@Test
	void parse_noSecurityCheck_returnsSecurityAnalyst() {
		assertEquals(Set.of(PdlcRole.SECURITY_ANALYST), RoleSkipParser.parse("no security check"));
	}

	// --- TECHNICAL_WRITER ---

	@Test
	void parse_skipDocs_returnsTechnicalWriter() {
		assertEquals(Set.of(PdlcRole.TECHNICAL_WRITER), RoleSkipParser.parse("skip docs"));
	}

	@Test
	void parse_noDocumentation_returnsTechnicalWriter() {
		assertEquals(Set.of(PdlcRole.TECHNICAL_WRITER), RoleSkipParser.parse("no documentation"));
	}

	// --- IMPLEMENTER only ("just implement" / "implement only") ---

	@Test
	void parse_justImplement_returnsAllExceptImplementer() {
		Set<PdlcRole> result = RoleSkipParser.parse("just implement this feature");
		Set<PdlcRole> expected = Set.of(
				PdlcRole.PM, PdlcRole.RESEARCHER, PdlcRole.ARCHITECT, PdlcRole.DESIGNER,
				PdlcRole.PLANNER, PdlcRole.REVIEWER, PdlcRole.TESTER, PdlcRole.SECURITY_ANALYST,
				PdlcRole.TECHNICAL_WRITER, PdlcRole.DEVOPS, PdlcRole.RETRO_ANALYST);
		assertEquals(expected, result);
		assertTrue(!result.contains(PdlcRole.IMPLEMENTER));
	}

	@Test
	void parse_implementOnly_returnsAllExceptImplementer() {
		Set<PdlcRole> result = RoleSkipParser.parse("implement only");
		assertEquals(11, result.size());
		assertTrue(!result.contains(PdlcRole.IMPLEMENTER));
	}

	@Test
	void parse_justImplementCaseInsensitive_returnsAllExceptImplementer() {
		Set<PdlcRole> result = RoleSkipParser.parse("Just Implement");
		assertEquals(11, result.size());
	}

	// --- PM + PLANNER ("skip planning") ---

	@Test
	void parse_skipPlanning_returnsPmAndPlanner() {
		assertEquals(Set.of(PdlcRole.PM, PdlcRole.PLANNER), RoleSkipParser.parse("skip planning"));
	}

	// --- RETRO_ANALYST ---

	@Test
	void parse_noRetro_returnsRetroAnalyst() {
		assertEquals(Set.of(PdlcRole.RETRO_ANALYST), RoleSkipParser.parse("no retro please"));
	}

	// --- DESIGNER ---

	@Test
	void parse_skipDesign_returnsDesigner() {
		assertEquals(Set.of(PdlcRole.DESIGNER), RoleSkipParser.parse("skip design"));
	}

	// --- RESEARCHER ---

	@Test
	void parse_skipResearch_returnsResearcher() {
		assertEquals(Set.of(PdlcRole.RESEARCHER), RoleSkipParser.parse("skip research"));
	}

	// --- DEVOPS ---

	@Test
	void parse_skipDevops_returnsDevops() {
		assertEquals(Set.of(PdlcRole.DEVOPS), RoleSkipParser.parse("skip devops"));
	}

	@Test
	void parse_noDeploy_returnsDevops() {
		assertEquals(Set.of(PdlcRole.DEVOPS), RoleSkipParser.parse("no deploy needed"));
	}

	// --- Multiple skip phrases in one command ---

	@Test
	void parse_skipReviewAndTests_returnsBothRoles() {
		Set<PdlcRole> result = RoleSkipParser.parse("skip review and skip tests");
		assertEquals(Set.of(PdlcRole.REVIEWER, PdlcRole.TESTER), result);
	}

	@Test
	void parse_skipSecurityAndDocs_returnsBothRoles() {
		Set<PdlcRole> result = RoleSkipParser.parse("no security check and no documentation");
		assertEquals(Set.of(PdlcRole.SECURITY_ANALYST, PdlcRole.TECHNICAL_WRITER), result);
	}

	@Test
	void parse_skipDesignAndResearch_returnsBothRoles() {
		Set<PdlcRole> result = RoleSkipParser.parse("skip design and skip research");
		assertEquals(Set.of(PdlcRole.DESIGNER, PdlcRole.RESEARCHER), result);
	}

	// --- "just implement" takes precedence and subsumes individual skips ---

	@Test
	void parse_justImplementWithOtherKeywords_returnsAllExceptImplementer() {
		// "just implement" is matched first and already covers REVIEWER; combining
		// with individual skips should still return all roles except IMPLEMENTER
		Set<PdlcRole> result = RoleSkipParser.parse("just implement, skip review");
		assertEquals(11, result.size());
		assertTrue(!result.contains(PdlcRole.IMPLEMENTER));
	}

	// --- Embedded in longer sentence ---

	@Test
	void parse_skipReviewEmbeddedInSentence_returnsReviewer() {
		assertEquals(Set.of(PdlcRole.REVIEWER),
				RoleSkipParser.parse("Build the auth module, skip review, push to prod"));
	}

	@Test
	void parse_skipTestsEmbeddedInSentence_returnsTester() {
		assertEquals(Set.of(PdlcRole.TESTER),
				RoleSkipParser.parse("Refactor the payment service, skip tests, fast track"));
	}
}
