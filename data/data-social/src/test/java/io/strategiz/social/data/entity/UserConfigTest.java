package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class UserConfigTest {

	@Test
	void defaults_areCorrect() {
		UserConfig config = UserConfig.defaults();
		assertEquals(3, config.getMaxConcurrentSparks());
		assertEquals(BigDecimal.ZERO, config.getSpendingLimit());
		assertTrue(config.getDomainAllowlist().isEmpty());
		assertTrue(config.getDomainBlocklist().isEmpty());
		assertTrue(config.getConfirmationOverrides().isEmpty());
	}

	@Test
	void tacticlUser_hasConfigField() {
		TacticlUser user = new TacticlUser();
		assertNull(user.getConfig());
		user.setConfig(UserConfig.defaults());
		assertNotNull(user.getConfig());
		assertEquals(3, user.getConfig().getMaxConcurrentSparks());
	}

}
