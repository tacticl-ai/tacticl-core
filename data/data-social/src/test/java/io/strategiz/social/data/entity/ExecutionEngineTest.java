package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExecutionEngineTest {

	@Test
	void hasThreeValues() {
		assertEquals(3, ExecutionEngine.values().length);
	}

	@Test
	void valuesAreDefined() {
		assertNotNull(ExecutionEngine.valueOf("CLAUDE_CODE"));
		assertNotNull(ExecutionEngine.valueOf("LEGACY"));
		assertNotNull(ExecutionEngine.valueOf("AUTO"));
	}

}
