package io.strategiz.social.business.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cidadel.framework.token.issuer.PasetoTokenIssuer;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.PairingCode;
import io.strategiz.social.data.entity.PairingSession;
import io.strategiz.social.data.repository.DeviceRepository;
import io.strategiz.social.data.repository.PairingCodeRepository;
import io.strategiz.social.data.repository.PairingSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DevicePairingServiceTest {

	@Mock
	private PairingCodeRepository pairingCodeRepository;

	@Mock
	private PairingSessionRepository pairingSessionRepository;

	@Mock
	private DeviceRepository deviceRepository;

	@Mock
	private PasetoTokenIssuer tokenIssuer;

	@InjectMocks
	private DevicePairingService devicePairingService;

	// ========== generatePairingCode ==========

	@Test
	void generatePairingCode_validUser_returnsCodeAndInvalidatesPrevious() {
		String userId = "user-123";

		PairingCode existingCode = new PairingCode();
		existingCode.setId("existing-id");
		existingCode.setUserId(userId);
		existingCode.setCode("111111");
		existingCode.setConsumed(false);

		when(pairingCodeRepository.findActiveByUserId(userId)).thenReturn(List.of(existingCode));

		PairingCode result = devicePairingService.generatePairingCode(userId);

		// Verify existing code was consumed
		assertTrue(existingCode.isConsumed());
		verify(pairingCodeRepository).save(existingCode, "existing-id");

		// Verify new code returned with correct fields
		assertNotNull(result);
		assertNotNull(result.getId());
		assertEquals(userId, result.getUserId());
		assertNotNull(result.getCode());
		assertEquals(6, result.getCode().length());
		assertFalse(result.isConsumed());
		assertNotNull(result.getExpiresAt());
		assertTrue(result.getExpiresAt().isAfter(Instant.now()));
		assertNotNull(result.getCreatedAt());

		// Verify new code was saved (second save call)
		verify(pairingCodeRepository, times(2)).save(any(PairingCode.class), anyString());
	}

	@Test
	void generatePairingCode_noPreviousCodes_returnsNewCode() {
		String userId = "user-456";

		when(pairingCodeRepository.findActiveByUserId(userId)).thenReturn(Collections.emptyList());

		PairingCode result = devicePairingService.generatePairingCode(userId);

		assertNotNull(result);
		assertEquals(userId, result.getUserId());
		assertNotNull(result.getCode());
		assertEquals(6, result.getCode().length());
		assertFalse(result.isConsumed());

		// Only one save call (the new code)
		verify(pairingCodeRepository, times(1)).save(any(PairingCode.class), anyString());
	}

	// ========== pairWithCode ==========

	@Test
	void pairWithCode_validCode_createsDeviceAndReturnsToken() {
		String code = "482901";
		String userId = "user-123";

		PairingCode pairingCode = new PairingCode();
		pairingCode.setId("code-id");
		pairingCode.setUserId(userId);
		pairingCode.setCode(code);
		pairingCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
		pairingCode.setConsumed(false);

		when(pairingCodeRepository.findByCode(code)).thenReturn(Optional.of(pairingCode));
		when(tokenIssuer.createAuthenticationToken(anyString(), anyList(), anyString(), any(Duration.class),
				anyBoolean()))
			.thenReturn("test-paseto-token");

		DevicePairingService.PairResult result = devicePairingService.pairWithCode(code, "My MacBook", "MACOS",
				"macos", Map.of("claude_code", true));

		// Verify result
		assertNotNull(result);
		assertNotNull(result.deviceId());
		assertEquals("test-paseto-token", result.sessionToken());

		// Verify code was consumed
		assertTrue(pairingCode.isConsumed());
		verify(pairingCodeRepository).save(pairingCode, "code-id");

		// Verify device was saved
		ArgumentCaptor<DeviceRegistration> deviceCaptor = ArgumentCaptor.forClass(DeviceRegistration.class);
		verify(deviceRepository).save(anyString(), deviceCaptor.capture(), anyString());
		DeviceRegistration savedDevice = deviceCaptor.getValue();
		assertEquals(userId, savedDevice.getUserId());
		assertEquals("My MacBook", savedDevice.getDeviceName());
		assertTrue(savedDevice.isActive());

		// Verify token was created with correct userId
		verify(tokenIssuer).createAuthenticationToken(eq(userId), anyList(), anyString(), any(Duration.class),
				anyBoolean());
	}

	@Test
	void pairWithCode_expiredCode_throwsException() {
		String code = "999999";

		// findByCode filters out expired codes and returns empty Optional
		when(pairingCodeRepository.findByCode(code)).thenReturn(Optional.empty());

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> devicePairingService.pairWithCode(code, "Device", "MACOS", "macos", Map.of()));

		assertEquals("Invalid or expired pairing code", ex.getMessage());
		verify(deviceRepository, never()).save(anyString(), any(), anyString());
	}

	@Test
	void pairWithCode_invalidCode_throwsException() {
		String code = "000000";

		when(pairingCodeRepository.findByCode(code)).thenReturn(Optional.empty());

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> devicePairingService.pairWithCode(code, "Device", "MACOS", "macos", Map.of()));

		assertEquals("Invalid or expired pairing code", ex.getMessage());
		verify(deviceRepository, never()).save(anyString(), any(), anyString());
	}

	@Test
	void pairWithCode_consumedCode_throwsException() {
		String code = "123456";

		// findByCode filters out consumed codes and returns empty Optional
		when(pairingCodeRepository.findByCode(code)).thenReturn(Optional.empty());

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> devicePairingService.pairWithCode(code, "Device", "MACOS", "macos", Map.of()));

		assertEquals("Invalid or expired pairing code", ex.getMessage());
		verify(deviceRepository, never()).save(anyString(), any(), anyString());
	}

	// ========== createPairingSession ==========

	@Test
	void createPairingSession_returnsSessionWithWaitingStatus() {
		PairingSession result = devicePairingService.createPairingSession();

		assertNotNull(result);
		assertNotNull(result.getId());
		assertNotNull(result.getSecret());
		assertEquals("waiting", result.getStatus());
		assertNotNull(result.getExpiresAt());
		assertTrue(result.getExpiresAt().isAfter(Instant.now()));
		assertNotNull(result.getCreatedAt());

		// Verify session was saved
		verify(pairingSessionRepository).save(any(PairingSession.class), anyString());
	}

	// ========== pairWithQr ==========

	@Test
	void pairWithQr_validSession_createsDeviceAndPairsSession() {
		String sessionId = "session-123";
		String secret = "session-secret";
		String userId = "user-789";

		PairingSession session = new PairingSession();
		session.setId(sessionId);
		session.setSecret(secret);
		session.setStatus("waiting");
		session.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

		when(pairingSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
		when(tokenIssuer.createAuthenticationToken(anyString(), anyList(), anyString(), any(Duration.class),
				anyBoolean()))
			.thenReturn("test-qr-token");

		DevicePairingService.PairResult result = devicePairingService.pairWithQr(sessionId, secret, userId,
				"My iPhone", "IPHONE", "ios", Map.of("push_notifications", true));

		// Verify result
		assertNotNull(result);
		assertNotNull(result.deviceId());
		assertEquals("test-qr-token", result.sessionToken());

		// Verify device was saved
		ArgumentCaptor<DeviceRegistration> deviceCaptor = ArgumentCaptor.forClass(DeviceRegistration.class);
		verify(deviceRepository).save(anyString(), deviceCaptor.capture(), anyString());
		DeviceRegistration savedDevice = deviceCaptor.getValue();
		assertEquals(userId, savedDevice.getUserId());
		assertEquals("My iPhone", savedDevice.getDeviceName());
		assertTrue(savedDevice.isActive());

		// Verify session was updated to "paired"
		assertEquals("paired", session.getStatus());
		assertNotNull(session.getDeviceId());
		assertEquals("test-qr-token", session.getSessionToken());
		assertEquals(userId, session.getUserId());
		assertEquals("My iPhone", session.getDeviceName());
		assertEquals("ios", session.getPlatform());
		verify(pairingSessionRepository).save(session, sessionId);
	}

	@Test
	void pairWithQr_invalidSecret_throwsException() {
		String sessionId = "session-123";

		PairingSession session = new PairingSession();
		session.setId(sessionId);
		session.setSecret("correct-secret");
		session.setStatus("waiting");
		session.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

		when(pairingSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> devicePairingService.pairWithQr(sessionId, "wrong-secret", "user-1", "Device", "MACOS",
						"macos", Map.of()));

		assertEquals("Invalid pairing session secret", ex.getMessage());
		verify(deviceRepository, never()).save(anyString(), any(), anyString());
	}

	@Test
	void pairWithQr_expiredSession_throwsException() {
		String sessionId = "session-expired";

		PairingSession session = new PairingSession();
		session.setId(sessionId);
		session.setSecret("the-secret");
		session.setStatus("waiting");
		session.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

		when(pairingSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> devicePairingService.pairWithQr(sessionId, "the-secret", "user-1", "Device", "MACOS",
						"macos", Map.of()));

		assertEquals("Pairing session has expired", ex.getMessage());
		verify(deviceRepository, never()).save(anyString(), any(), anyString());
	}

	@Test
	void pairWithQr_alreadyPaired_throwsException() {
		String sessionId = "session-paired";

		PairingSession session = new PairingSession();
		session.setId(sessionId);
		session.setSecret("the-secret");
		session.setStatus("paired");
		session.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

		when(pairingSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> devicePairingService.pairWithQr(sessionId, "the-secret", "user-1", "Device", "MACOS",
						"macos", Map.of()));

		assertEquals("Pairing session is no longer available", ex.getMessage());
		verify(deviceRepository, never()).save(anyString(), any(), anyString());
	}

	// ========== getPairingSessionStatus ==========

	@Test
	void getPairingSessionStatus_waitingSession_returnsWaiting() {
		String sessionId = "session-wait";
		String secret = "wait-secret";

		PairingSession session = new PairingSession();
		session.setId(sessionId);
		session.setSecret(secret);
		session.setStatus("waiting");
		session.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

		when(pairingSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

		Map<String, Object> result = devicePairingService.getPairingSessionStatus(sessionId, secret);

		assertNotNull(result);
		assertEquals("waiting", result.get("status"));
		assertFalse(result.containsKey("deviceId"));
		assertFalse(result.containsKey("sessionToken"));
	}

	@Test
	void getPairingSessionStatus_pairedSession_returnsTokenAndDeviceId() {
		String sessionId = "session-done";
		String secret = "done-secret";

		PairingSession session = new PairingSession();
		session.setId(sessionId);
		session.setSecret(secret);
		session.setStatus("paired");
		session.setDeviceId("device-abc");
		session.setSessionToken("token-xyz");
		session.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

		when(pairingSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

		Map<String, Object> result = devicePairingService.getPairingSessionStatus(sessionId, secret);

		assertNotNull(result);
		assertEquals("paired", result.get("status"));
		assertEquals("device-abc", result.get("deviceId"));
		assertEquals("token-xyz", result.get("sessionToken"));
	}

	@Test
	void getPairingSessionStatus_invalidSecret_throwsException() {
		String sessionId = "session-sec";

		PairingSession session = new PairingSession();
		session.setId(sessionId);
		session.setSecret("real-secret");
		session.setStatus("waiting");
		session.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

		when(pairingSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> devicePairingService.getPairingSessionStatus(sessionId, "fake-secret"));

		assertEquals("Invalid pairing session secret", ex.getMessage());
	}

}
