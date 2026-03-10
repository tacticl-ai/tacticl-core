package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ClaudeCodeConfigTest {

	@Test
	void defaults_areCorrect() {
		ClaudeCodeConfig config = ClaudeCodeConfig.defaults();
		assertEquals("claude-opus-4-6", config.getModel());
		assertEquals(25, config.getMaxTurns());
		assertEquals(new BigDecimal("5.00"), config.getMaxBudgetUsd());
		assertEquals("acceptEdits", config.getPermissionMode());
		assertNull(config.getAllowedTools());
		assertNull(config.getDisallowedTools());
		assertNull(config.getMcpServers());
		assertNull(config.getSystemPromptOverride());
	}

	@Test
	void settersAndGetters_work() {
		ClaudeCodeConfig config = new ClaudeCodeConfig();
		config.setModel("claude-sonnet-4-6");
		config.setMaxTurns(10);
		config.setMaxBudgetUsd(new BigDecimal("25.00"));
		config.setPermissionMode("bypassPermissions");

		assertEquals("claude-sonnet-4-6", config.getModel());
		assertEquals(10, config.getMaxTurns());
		assertEquals(new BigDecimal("25.00"), config.getMaxBudgetUsd());
		assertEquals("bypassPermissions", config.getPermissionMode());
	}

}
