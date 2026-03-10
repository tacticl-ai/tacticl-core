package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DeviceSettingsTest {

	@Test
	void defaults_areCorrect() {
		DeviceSettings settings = DeviceSettings.defaults();
		assertEquals(1, settings.getMaxDaemons());
		assertFalse(settings.isAutoWake());
		assertEquals(0, settings.getPriority());
	}

	@Test
	void deviceRegistration_hasSettingsField() {
		DeviceRegistration device = new DeviceRegistration();
		assertNull(device.getSettings());
		device.setSettings(DeviceSettings.defaults());
		assertNotNull(device.getSettings());
		assertEquals(1, device.getSettings().getMaxDaemons());
	}

	@Test
	void deviceType_isDesktop_correctForAllTypes() {
		assertTrue(DeviceType.MACOS.isDesktop());
		assertTrue(DeviceType.WINDOWS.isDesktop());
		assertTrue(DeviceType.LINUX.isDesktop());
		assertFalse(DeviceType.IPHONE.isDesktop());
		assertFalse(DeviceType.ANDROID.isDesktop());
	}

	@Test
	void defaults_executionEngine_isClaudeCode() {
		DeviceSettings settings = DeviceSettings.defaults();
		assertEquals(ExecutionEngine.CLAUDE_CODE, settings.getExecutionEngine());
	}

	@Test
	void defaults_claudeCodeConfig_isNull() {
		DeviceSettings settings = DeviceSettings.defaults();
		assertNull(settings.getClaudeCodeConfig());
	}

	@Test
	void setExecutionEngine_andGet() {
		DeviceSettings settings = new DeviceSettings();
		settings.setExecutionEngine(ExecutionEngine.LEGACY);
		assertEquals(ExecutionEngine.LEGACY, settings.getExecutionEngine());
	}

	@Test
	void setClaudeCodeConfig_andGet() {
		DeviceSettings settings = new DeviceSettings();
		ClaudeCodeConfig config = ClaudeCodeConfig.defaults();
		settings.setClaudeCodeConfig(config);
		assertNotNull(settings.getClaudeCodeConfig());
		assertEquals("claude-opus-4-6", settings.getClaudeCodeConfig().getModel());
	}

	@Test
	void deviceRegistration_claudeCodeVersion_getSet() {
		DeviceRegistration device = new DeviceRegistration();
		assertNull(device.getClaudeCodeVersion());
		device.setClaudeCodeVersion("1.0.32");
		assertEquals("1.0.32", device.getClaudeCodeVersion());
	}

}
