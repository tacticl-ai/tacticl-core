package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AiRoleOverrideTest {

	@Test
	void defaultConstructor_fieldsAreNull() {
		AiRoleOverride override = new AiRoleOverride();
		assertNull(override.getId());
		assertNull(override.getRole());
		assertNull(override.getEngineId());
		assertNull(override.getModel());
		assertNull(override.getUpdatedBy());
	}

	@Test
	void setAndGetRole() {
		AiRoleOverride override = new AiRoleOverride();
		override.setRole("IMPLEMENTER");
		assertEquals("IMPLEMENTER", override.getRole());
	}

	@Test
	void setAndGetEngineId() {
		AiRoleOverride override = new AiRoleOverride();
		override.setEngineId("anthropic-agentic");
		assertEquals("anthropic-agentic", override.getEngineId());
	}

	@Test
	void setAndGetModel() {
		AiRoleOverride override = new AiRoleOverride();
		override.setModel("claude-opus-4-6");
		assertEquals("claude-opus-4-6", override.getModel());
	}

	@Test
	void setAndGetUpdatedBy() {
		AiRoleOverride override = new AiRoleOverride();
		override.setUpdatedBy("admin-user-123");
		assertEquals("admin-user-123", override.getUpdatedBy());
	}

	@Test
	void setAndGetId() {
		AiRoleOverride override = new AiRoleOverride();
		override.setId("IMPLEMENTER");
		assertEquals("IMPLEMENTER", override.getId());
	}

	@Test
	void allFieldsSetTogether() {
		AiRoleOverride override = new AiRoleOverride();
		override.setId("REVIEWER");
		override.setRole("REVIEWER");
		override.setEngineId("openai-agentic");
		override.setModel("gpt-4o");
		override.setUpdatedBy("admin-456");

		assertEquals("REVIEWER", override.getId());
		assertEquals("REVIEWER", override.getRole());
		assertEquals("openai-agentic", override.getEngineId());
		assertEquals("gpt-4o", override.getModel());
		assertEquals("admin-456", override.getUpdatedBy());
	}

	@Test
	void extendsBaseEntity() {
		AiRoleOverride override = new AiRoleOverride();
		assertInstanceOf(io.cidadel.data.base.entity.BaseEntity.class, override);
	}

}
