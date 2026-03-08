package io.strategiz.social.business.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.strategiz.social.data.entity.AccessLevel;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceSession;
import io.strategiz.social.data.entity.DeviceType;
import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.RepoGrant;
import io.strategiz.social.data.entity.RepoProvider;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.DeviceRepository;
import io.strategiz.social.data.repository.DeviceSessionRepository;
import io.strategiz.social.data.repository.RepoGrantRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionStatusSkillTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Mock
	private DeviceRepository deviceRepository;

	@Mock
	private DeviceSessionRepository sessionRepository;

	@Mock
	private SocialIntegrationRepository integrationRepository;

	@Mock
	private RepoGrantRepository repoGrantRepository;

	@InjectMocks
	private ConnectionStatusSkill skill;

	@Test
	void getName_returnsConnectionStatus() {
		assertEquals("connection_status", skill.getName());
	}

	@Test
	void getConfirmationTier_returns0() {
		assertEquals(0, skill.getConfirmationTier());
	}

	@Test
	void execute_allEmpty_returnsNoneSections() {
		when(deviceRepository.findActiveByUserId("user-1")).thenReturn(Collections.emptyList());
		when(integrationRepository.findAllByUserId("user-1")).thenReturn(Collections.emptyList());
		when(repoGrantRepository.findActiveByUserId("user-1")).thenReturn(Collections.emptyList());

		ObjectNode input = MAPPER.createObjectNode();
		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Devices (0)"));
		assertTrue(result.contains("Social Integrations (0)"));
		assertTrue(result.contains("Repositories (0)"));
	}

	@Test
	void execute_withAllResources_returnsFormattedOverview() {
		// Device
		DeviceRegistration device = new DeviceRegistration();
		device.setId("device-1");
		device.setDeviceName("My MacBook");
		device.setDeviceType(DeviceType.MACOS);
		when(deviceRepository.findActiveByUserId("user-1")).thenReturn(List.of(device));

		// Session (device online)
		DeviceSession session = new DeviceSession();
		session.setDeviceId("device-1");
		when(sessionRepository.findActiveByUserId("user-1")).thenReturn(List.of(session));

		// Integration
		SocialIntegration integ = new SocialIntegration();
		integ.setPlatform(PlatformType.TWITTER);
		integ.setPlatformUsername("johndoe");
		integ.setDisabled(false);
		when(integrationRepository.findAllByUserId("user-1")).thenReturn(List.of(integ));

		// Repo
		RepoGrant repo = new RepoGrant();
		repo.setRepoFullName("owner/my-repo");
		repo.setProvider(RepoProvider.GITHUB);
		repo.setAccessLevel(AccessLevel.READ);
		when(repoGrantRepository.findActiveByUserId("user-1")).thenReturn(List.of(repo));

		ObjectNode input = MAPPER.createObjectNode();
		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Devices (1)"));
		assertTrue(result.contains("My MacBook"));
		assertTrue(result.contains("MACOS"));
		assertTrue(result.contains("online"));
		assertTrue(result.contains("Social Integrations (1)"));
		assertTrue(result.contains("Twitter/X"));
		assertTrue(result.contains("@johndoe"));
		assertTrue(result.contains("active"));
		assertTrue(result.contains("Repositories (1)"));
		assertTrue(result.contains("owner/my-repo"));
		assertTrue(result.contains("GITHUB"));
	}

	@Test
	void execute_deviceOffline_showsOffline() {
		DeviceRegistration device = new DeviceRegistration();
		device.setId("device-2");
		device.setDeviceName("My iPhone");
		device.setDeviceType(DeviceType.IPHONE);
		when(deviceRepository.findActiveByUserId("user-1")).thenReturn(List.of(device));

		// No active session = offline
		when(sessionRepository.findActiveByUserId("user-1")).thenReturn(Collections.emptyList());
		when(integrationRepository.findAllByUserId("user-1")).thenReturn(Collections.emptyList());
		when(repoGrantRepository.findActiveByUserId("user-1")).thenReturn(Collections.emptyList());

		ObjectNode input = MAPPER.createObjectNode();
		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("My iPhone"));
		assertTrue(result.contains("offline"));
		assertFalse(result.contains("online"));
	}

	@Test
	void execute_disabledIntegration_showsDisabled() {
		when(deviceRepository.findActiveByUserId("user-1")).thenReturn(Collections.emptyList());
		when(repoGrantRepository.findActiveByUserId("user-1")).thenReturn(Collections.emptyList());

		SocialIntegration integ = new SocialIntegration();
		integ.setPlatform(PlatformType.LINKEDIN);
		integ.setDisabled(true);
		when(integrationRepository.findAllByUserId("user-1")).thenReturn(List.of(integ));

		ObjectNode input = MAPPER.createObjectNode();
		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("LinkedIn"));
		assertTrue(result.contains("disabled"));
	}

	@Test
	void execute_multipleDevices_showsMixedOnlineStatus() {
		DeviceRegistration online = new DeviceRegistration();
		online.setId("d-online");
		online.setDeviceName("Desktop");
		online.setDeviceType(DeviceType.MACOS);

		DeviceRegistration offline = new DeviceRegistration();
		offline.setId("d-offline");
		offline.setDeviceName("Tablet");
		offline.setDeviceType(DeviceType.IPHONE);

		when(deviceRepository.findActiveByUserId("user-1")).thenReturn(List.of(online, offline));

		DeviceSession sess = new DeviceSession();
		sess.setDeviceId("d-online");
		when(sessionRepository.findActiveByUserId("user-1")).thenReturn(List.of(sess));

		when(integrationRepository.findAllByUserId("user-1")).thenReturn(Collections.emptyList());
		when(repoGrantRepository.findActiveByUserId("user-1")).thenReturn(Collections.emptyList());

		ObjectNode input = MAPPER.createObjectNode();
		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Devices (2)"));
		assertTrue(result.contains("Desktop"));
		assertTrue(result.contains("online"));
		assertTrue(result.contains("Tablet"));
		assertTrue(result.contains("offline"));
	}

}
