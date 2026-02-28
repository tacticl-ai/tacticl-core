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

}
