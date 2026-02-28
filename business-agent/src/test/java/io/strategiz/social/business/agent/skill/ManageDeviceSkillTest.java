package io.strategiz.social.business.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strategiz.social.business.agent.service.DevicePairingService;
import io.strategiz.social.business.agent.service.DeviceRegistryService;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.DeviceSettings;
import io.strategiz.social.data.entity.DeviceState;
import io.strategiz.social.data.entity.DeviceType;
import io.strategiz.social.data.entity.PairingCode;
import io.strategiz.social.data.repository.DeviceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageDeviceSkillTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Mock
	private DevicePairingService devicePairingService;

	@Mock
	private DeviceRepository deviceRepository;

	@Mock
	private DeviceRegistryService deviceRegistryService;

	@InjectMocks
	private ManageDeviceSkill skill;

	@Test
	void getName_returnsManageDevice() {
		assertEquals("manage_device", skill.getName());
	}

	@Test
	void getConfirmationTier_returns1() {
		assertEquals(1, skill.getConfirmationTier());
	}

	@Test
	void execute_pair_returnsPairingCode() {
		PairingCode pairingCode = new PairingCode();
		pairingCode.setCode("123456");
		when(devicePairingService.generatePairingCode("user-1")).thenReturn(pairingCode);

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "pair");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Pairing code: 123456"));
		assertTrue(result.contains("5 minutes"));
	}

	@Test
	void execute_unpair_deactivatesDevice() {
		when(deviceRegistryService.revokeDevice("device-1", "user-1")).thenReturn(true);

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "unpair");
		input.put("device_id", "device-1");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("unpaired and deactivated"));
		verify(deviceRegistryService).revokeDevice("device-1", "user-1");
	}

	@Test
	void execute_unpair_deviceNotFound_returnsError() {
		when(deviceRegistryService.revokeDevice("device-x", "user-1")).thenReturn(false);

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "unpair");
		input.put("device_id", "device-x");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Device not found or already unpaired"));
	}

	@Test
	void execute_unpair_missingDeviceId_returnsError() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "unpair");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("device_id is required"));
	}

	@Test
	void execute_updateSettings_updatesDevice() {
		DeviceRegistration device = new DeviceRegistration();
		device.setId("device-1");
		device.setUserId("user-1");
		device.setDeviceName("My MacBook");
		device.setSettings(DeviceSettings.defaults());
		when(deviceRepository.findById("user-1", "device-1")).thenReturn(Optional.of(device));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "update_settings");
		input.put("device_id", "device-1");
		input.put("max_daemons", 4);
		input.put("auto_wake", true);
		input.put("priority", 10);

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Device settings updated"));
		assertTrue(result.contains("Max daemons: 4"));
		assertTrue(result.contains("Auto wake: true"));
		assertTrue(result.contains("Priority: 10"));

		ArgumentCaptor<DeviceRegistration> captor = ArgumentCaptor.forClass(DeviceRegistration.class);
		verify(deviceRepository).save(eq("user-1"), captor.capture(), eq("device-1"));
		DeviceSettings saved = captor.getValue().getSettings();
		assertEquals(4, saved.getMaxDaemons());
		assertTrue(saved.isAutoWake());
		assertEquals(10, saved.getPriority());
	}

	@Test
	void execute_updateSettings_createsDefaultsWhenNull() {
		DeviceRegistration device = new DeviceRegistration();
		device.setId("device-1");
		device.setUserId("user-1");
		device.setDeviceName("My MacBook");
		device.setSettings(null);
		when(deviceRepository.findById("user-1", "device-1")).thenReturn(Optional.of(device));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "update_settings");
		input.put("device_id", "device-1");
		input.put("max_daemons", 2);

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Device settings updated"));
		assertTrue(result.contains("Max daemons: 2"));

		ArgumentCaptor<DeviceRegistration> captor = ArgumentCaptor.forClass(DeviceRegistration.class);
		verify(deviceRepository).save(eq("user-1"), captor.capture(), eq("device-1"));
		assertEquals(2, captor.getValue().getSettings().getMaxDaemons());
	}

	@Test
	void execute_updateSettings_deviceNotFound_returnsError() {
		when(deviceRepository.findById("user-1", "device-x")).thenReturn(Optional.empty());

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "update_settings");
		input.put("device_id", "device-x");
		input.put("max_daemons", 3);

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Device not found: device-x"));
	}

	@Test
	void execute_updateSettings_noFields_returnsError() {
		DeviceRegistration device = new DeviceRegistration();
		device.setId("device-1");
		device.setUserId("user-1");
		device.setDeviceName("My MacBook");
		when(deviceRepository.findById("user-1", "device-1")).thenReturn(Optional.of(device));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "update_settings");
		input.put("device_id", "device-1");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("No settings fields provided"));
	}

	@Test
	void execute_list_returnsDevices() {
		DeviceRegistration device = new DeviceRegistration();
		device.setId("device-1");
		device.setDeviceName("My MacBook");
		device.setDeviceType(DeviceType.MACOS);
		device.setState(DeviceState.ACTIVE);
		DeviceSettings settings = new DeviceSettings();
		settings.setMaxDaemons(2);
		settings.setAutoWake(true);
		settings.setPriority(5);
		device.setSettings(settings);
		when(deviceRegistryService.listDevices("user-1")).thenReturn(List.of(device));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "list");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("My MacBook"));
		assertTrue(result.contains("MACOS"));
		assertTrue(result.contains("device-1"));
		assertTrue(result.contains("max_daemons=2"));
		assertTrue(result.contains("auto_wake=true"));
		assertTrue(result.contains("priority=5"));
	}

	@Test
	void execute_list_noDevices_returnsMessage() {
		when(deviceRegistryService.listDevices("user-1")).thenReturn(List.of());

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "list");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("don't have any active devices"));
		assertTrue(result.contains("pair"));
	}

	@Test
	void execute_unknownAction_returnsError() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "restart");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Unknown action: restart"));
	}

}
