package io.strategiz.social.business.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strategiz.social.business.agent.service.UserConfigService;
import io.strategiz.social.data.entity.UserConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageSettingsSkillTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Mock
	private UserConfigService userConfigService;

	@InjectMocks
	private ManageSettingsSkill skill;

	@Test
	void getName_returnsManageSettings() {
		assertEquals("manage_settings", skill.getName());
	}

	@Test
	void getConfirmationTier_returnsTier1() {
		assertEquals(1, skill.getConfirmationTier());
	}

	@Test
	void execute_getAction_returnsFormattedConfig() {
		UserConfig config = new UserConfig();
		config.setMaxConcurrentSparks(5);
		config.setSpendingLimit(new BigDecimal("25.00"));
		config.setDomainAllowlist(List.of("github.com", "google.com"));
		config.setDomainBlocklist(List.of("evil.com"));
		when(userConfigService.getConfig("user-1")).thenReturn(config);

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "get");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Max concurrent sparks: 5"));
		assertTrue(result.contains("$25.00"));
		assertTrue(result.contains("github.com"));
		assertTrue(result.contains("google.com"));
		assertTrue(result.contains("evil.com"));
	}

	@Test
	void execute_getAction_defaultConfig_showsNone() {
		when(userConfigService.getConfig("user-1")).thenReturn(UserConfig.defaults());

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "get");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Max concurrent sparks: 3"));
		assertTrue(result.contains("$0"));
		assertTrue(result.contains("(none)"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void execute_updateAction_delegatesToService() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "update");
		input.put("max_concurrent_sparks", 10);
		input.put("spending_limit", "100.00");
		ArrayNode allowlist = input.putArray("domain_allowlist");
		allowlist.add("github.com");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Settings updated successfully"));

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(userConfigService).updateConfig(eq("user-1"), captor.capture());
		Map<String, Object> updates = captor.getValue();
		assertEquals(10, updates.get("maxConcurrentSparks"));
		assertEquals("100.00", updates.get("spendingLimit"));
		assertEquals(List.of("github.com"), updates.get("domainAllowlist"));
	}

	@Test
	void execute_updateAction_noFields_returnsError() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "update");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("No settings fields provided"));
	}

	@Test
	void execute_updateAction_serviceThrows_returnsError() {
		doThrow(new RuntimeException("User not found: user-x"))
				.when(userConfigService).updateConfig(eq("user-x"), anyMap());

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "update");
		input.put("max_concurrent_sparks", 5);

		String result = skill.execute(input, "user-x");

		assertTrue(result.contains("Failed to update settings"));
		assertTrue(result.contains("User not found: user-x"));
	}

	@Test
	void execute_unknownAction_returnsError() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "delete");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Unknown action: delete"));
	}

	@Test
	void execute_updateDomainBlocklist_delegatesToService() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "update");
		ArrayNode blocklist = input.putArray("domain_blocklist");
		blocklist.add("spam.com");
		blocklist.add("phishing.net");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Settings updated successfully"));
		assertTrue(result.contains("domainBlocklist"));
	}

}
