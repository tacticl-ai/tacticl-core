package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.TacticlUser;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.data.repository.TacticlUserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserConfigService {

	private final TacticlUserRepository userRepository;

	public UserConfigService(TacticlUserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/** Get user config, returning defaults if user has no config set. */
	public UserConfig getConfig(String userId) {
		return userRepository.findById(userId)
				.map(TacticlUser::getConfig)
				.orElse(UserConfig.defaults());
	}

	/** Update specific config fields (partial update). */
	@SuppressWarnings("unchecked")
	public void updateConfig(String userId, Map<String, Object> updates) {
		TacticlUser user = userRepository.findById(userId)
				.orElseThrow(() -> new RuntimeException("User not found: " + userId));
		UserConfig config = user.getConfig() != null ? user.getConfig() : UserConfig.defaults();

		if (updates.containsKey("maxConcurrentSparks")) {
			config.setMaxConcurrentSparks(((Number) updates.get("maxConcurrentSparks")).intValue());
		}
		if (updates.containsKey("spendingLimit")) {
			config.setSpendingLimit(new BigDecimal(updates.get("spendingLimit").toString()));
		}
		if (updates.containsKey("domainAllowlist")) {
			config.setDomainAllowlist((List<String>) updates.get("domainAllowlist"));
		}
		if (updates.containsKey("domainBlocklist")) {
			config.setDomainBlocklist((List<String>) updates.get("domainBlocklist"));
		}
		if (updates.containsKey("confirmationOverrides")) {
			config.setConfirmationOverrides((Map<String, Integer>) updates.get("confirmationOverrides"));
		}

		user.setConfig(config);
		userRepository.save(user, userId);
	}

}
