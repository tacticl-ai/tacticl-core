package io.strategiz.social.business.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.TacticlUser;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.data.repository.TacticlUserRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserConfigServiceTest {

	@Mock
	private TacticlUserRepository userRepository;

	@InjectMocks
	private UserConfigService userConfigService;

	@Test
	void getConfig_userHasNoConfig_returnsDefaults() {
		TacticlUser user = new TacticlUser();
		user.setConfig(null);
		when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
		UserConfig config = userConfigService.getConfig("user-1");
		assertEquals(3, config.getMaxConcurrentSparks());
		assertEquals(BigDecimal.ZERO, config.getSpendingLimit());
	}

	@Test
	void getConfig_userNotFound_returnsDefaults() {
		when(userRepository.findById("missing")).thenReturn(Optional.empty());
		UserConfig config = userConfigService.getConfig("missing");
		assertEquals(3, config.getMaxConcurrentSparks());
	}

	@Test
	void getConfig_userHasConfig_returnsIt() {
		TacticlUser user = new TacticlUser();
		UserConfig existing = new UserConfig();
		existing.setMaxConcurrentSparks(5);
		user.setConfig(existing);
		when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
		UserConfig config = userConfigService.getConfig("user-1");
		assertEquals(5, config.getMaxConcurrentSparks());
	}

	@Test
	void updateConfig_mergesFields() {
		TacticlUser user = new TacticlUser();
		user.setConfig(UserConfig.defaults());
		when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

		userConfigService.updateConfig("user-1", Map.of("maxConcurrentSparks", 5));

		verify(userRepository).save(argThat(u ->
				u.getConfig().getMaxConcurrentSparks() == 5), eq("user-1"));
	}

	@Test
	void updateConfig_userNotFound_throws() {
		when(userRepository.findById("missing")).thenReturn(Optional.empty());
		assertThrows(RuntimeException.class, () ->
				userConfigService.updateConfig("missing", Map.of("maxConcurrentSparks", 5)));
	}

}
