package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.TacticlUser;
import io.strategiz.social.data.repository.TacticlUserRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ensures a TacticlUser record exists in Firestore for each authenticated user. Called
 * from AgentController before executing agent commands.
 */
@Service
public class UserProvisioningService {

	private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

	private final TacticlUserRepository userRepository;

	public UserProvisioningService(TacticlUserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Check if a TacticlUser record exists for the given userId. If not, create one with
	 * default preferences.
	 */
	public void ensureUserExists(String userId) {
		Optional<TacticlUser> existing = userRepository.findById(userId);
		if (existing.isPresent()) {
			return;
		}

		log.info("Provisioning new TacticlUser for userId: {}", userId);
		TacticlUser user = new TacticlUser();
		user.setId(userId);
		user.setCreatedAt(Instant.now());
		user.setOnboardingComplete(false);
		userRepository.save(user, userId);
	}

}
