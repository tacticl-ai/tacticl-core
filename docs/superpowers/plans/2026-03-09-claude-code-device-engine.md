# Claude Code Device Execution Engine — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Claude Code Agent SDK config support to the cloud side so desktop devices can use Claude Code as an execution engine.

**Architecture:** Cloud-side only (Phase 1 of the rollout). Add `ExecutionEngine` enum and `ClaudeCodeConfig` embedded entity to `DeviceSettings`. Update all consumers: REST DTOs, SettingsController, ManageDeviceSkill, ConnectionStatusSkill, DeviceWebSocketHandler (2 new message types), and DeviceRegistration (new `claudeCodeVersion` field). No new endpoints — existing settings endpoints handle the new fields transparently.

**Tech Stack:** Java 25, Spring Boot 4.0.3, Firestore, Jackson 3 (`tools.jackson.*`), JUnit 6 + Mockito

**Spec:** `docs/superpowers/specs/2026-03-09-claude-code-device-engine-design.md`

---

## File Structure

**New files:**
- `data/data-social/src/main/java/io/strategiz/social/data/entity/ExecutionEngine.java` — enum (CLAUDE_CODE, LEGACY, AUTO)
- `data/data-social/src/main/java/io/strategiz/social/data/entity/ClaudeCodeConfig.java` — embedded config entity
- `data/data-social/src/test/java/io/strategiz/social/data/entity/ClaudeCodeConfigTest.java` — unit tests
- `data/data-social/src/test/java/io/strategiz/social/data/entity/ExecutionEngineTest.java` — enum tests

**Modified files:**
- `data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceSettings.java` — add executionEngine + claudeCodeConfig fields
- `data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceRegistration.java` — add claudeCodeVersion field
- `data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceType.java` — add `isDesktop()` helper
- `data/data-social/src/test/java/io/strategiz/social/data/entity/DeviceSettingsTest.java` — update defaults tests
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/DeviceSettingsResponse.java` — add new fields
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/UpdateDeviceSettingsRequest.java` — add new fields
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/SettingsController.java` — map new fields
- `service/service-agent/src/main/java/io/strategiz/social/service/agent/websocket/DeviceWebSocketHandler.java` — handle 2 new message types
- `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/ManageDeviceSkill.java` — add execution_engine + claude_code_config params
- `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/ConnectionStatusSkill.java` — show engine info in output
- `business/business-agent/src/test/java/io/strategiz/social/business/agent/skill/ManageDeviceSkillTest.java` — new test cases
- `business/business-agent/src/test/java/io/strategiz/social/business/agent/skill/ConnectionStatusSkillTest.java` — update assertions

---

## Chunk 1: Data Layer — Entities and Tests

### Task 1: Create ExecutionEngine enum

**Files:**
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/ExecutionEngine.java`
- Create: `data/data-social/src/test/java/io/strategiz/social/data/entity/ExecutionEngineTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExecutionEngineTest {

	@Test
	void hasThreeValues() {
		assertEquals(3, ExecutionEngine.values().length);
	}

	@Test
	void valuesAreDefined() {
		assertNotNull(ExecutionEngine.valueOf("CLAUDE_CODE"));
		assertNotNull(ExecutionEngine.valueOf("LEGACY"));
		assertNotNull(ExecutionEngine.valueOf("AUTO"));
	}

}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.ExecutionEngineTest" --info`
Expected: FAIL — `ExecutionEngine` class does not exist

- [ ] **Step 3: Write the implementation**

```java
package io.strategiz.social.data.entity;

/** Execution engine for device spark processing. Desktop devices default to CLAUDE_CODE. */
public enum ExecutionEngine {

	/** Claude Code Agent SDK — full agentic execution with file/bash/web/MCP/subagents. */
	CLAUDE_CODE,

	/** Existing command-based daemon protocol (TERMINAL_CMD, OPEN_URL, etc.). */
	LEGACY,

	/** Choose engine per-spark based on type and complexity. */
	AUTO

}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.ExecutionEngineTest" --info`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/entity/ExecutionEngine.java data/data-social/src/test/java/io/strategiz/social/data/entity/ExecutionEngineTest.java
git commit -m "feat: add ExecutionEngine enum for device engine selection"
```

---

### Task 2: Create ClaudeCodeConfig entity

**Files:**
- Create: `data/data-social/src/main/java/io/strategiz/social/data/entity/ClaudeCodeConfig.java`
- Create: `data/data-social/src/test/java/io/strategiz/social/data/entity/ClaudeCodeConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ClaudeCodeConfigTest {

	@Test
	void defaults_areCorrect() {
		ClaudeCodeConfig config = ClaudeCodeConfig.defaults();
		assertEquals("claude-opus-4-6", config.getModel());
		assertEquals(25, config.getMaxTurns());
		assertEquals(new BigDecimal("5.00"), config.getMaxBudgetUsd());
		assertEquals("acceptEdits", config.getPermissionMode());
		assertNull(config.getAllowedTools());
		assertNull(config.getDisallowedTools());
		assertNull(config.getMcpServers());
		assertNull(config.getSystemPromptOverride());
	}

	@Test
	void settersAndGetters_work() {
		ClaudeCodeConfig config = new ClaudeCodeConfig();
		config.setModel("claude-sonnet-4-6");
		config.setMaxTurns(10);
		config.setMaxBudgetUsd(new BigDecimal("25.00"));
		config.setPermissionMode("bypassPermissions");

		assertEquals("claude-sonnet-4-6", config.getModel());
		assertEquals(10, config.getMaxTurns());
		assertEquals(new BigDecimal("25.00"), config.getMaxBudgetUsd());
		assertEquals("bypassPermissions", config.getPermissionMode());
	}

}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.ClaudeCodeConfigTest" --info`
Expected: FAIL — `ClaudeCodeConfig` class does not exist

- [ ] **Step 3: Write the implementation**

```java
package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Claude Code Agent SDK configuration, embedded in DeviceSettings for desktop devices. */
@IgnoreExtraProperties
public class ClaudeCodeConfig {

	private String model = "claude-opus-4-6";

	private int maxTurns = 25;

	private BigDecimal maxBudgetUsd = new BigDecimal("5.00");

	private List<String> allowedTools;

	private List<String> disallowedTools;

	private Map<String, Object> mcpServers;

	private String permissionMode = "acceptEdits";

	private String systemPromptOverride;

	public ClaudeCodeConfig() {}

	public static ClaudeCodeConfig defaults() {
		return new ClaudeCodeConfig();
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public int getMaxTurns() {
		return maxTurns;
	}

	public void setMaxTurns(int maxTurns) {
		this.maxTurns = maxTurns;
	}

	public BigDecimal getMaxBudgetUsd() {
		return maxBudgetUsd;
	}

	public void setMaxBudgetUsd(BigDecimal maxBudgetUsd) {
		this.maxBudgetUsd = maxBudgetUsd;
	}

	public List<String> getAllowedTools() {
		return allowedTools;
	}

	public void setAllowedTools(List<String> allowedTools) {
		this.allowedTools = allowedTools;
	}

	public List<String> getDisallowedTools() {
		return disallowedTools;
	}

	public void setDisallowedTools(List<String> disallowedTools) {
		this.disallowedTools = disallowedTools;
	}

	public Map<String, Object> getMcpServers() {
		return mcpServers;
	}

	public void setMcpServers(Map<String, Object> mcpServers) {
		this.mcpServers = mcpServers;
	}

	public String getPermissionMode() {
		return permissionMode;
	}

	public void setPermissionMode(String permissionMode) {
		this.permissionMode = permissionMode;
	}

	public String getSystemPromptOverride() {
		return systemPromptOverride;
	}

	public void setSystemPromptOverride(String systemPromptOverride) {
		this.systemPromptOverride = systemPromptOverride;
	}

}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.ClaudeCodeConfigTest" --info`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/entity/ClaudeCodeConfig.java data/data-social/src/test/java/io/strategiz/social/data/entity/ClaudeCodeConfigTest.java
git commit -m "feat: add ClaudeCodeConfig entity for desktop device Agent SDK settings"
```

---

### Task 3: Add isDesktop() helper to DeviceType

**Files:**
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceType.java:17-23`
- Modify: `data/data-social/src/test/java/io/strategiz/social/data/entity/DeviceSettingsTest.java`

- [ ] **Step 1: Write the failing test**

Add to `DeviceSettingsTest.java` (reusing existing test file since it already tests device entities):

```java
@Test
void deviceType_isDesktop_correctForAllTypes() {
	assertTrue(DeviceType.MACOS.isDesktop());
	assertTrue(DeviceType.WINDOWS.isDesktop());
	assertTrue(DeviceType.LINUX.isDesktop());
	assertFalse(DeviceType.IPHONE.isDesktop());
	assertFalse(DeviceType.ANDROID.isDesktop());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.DeviceSettingsTest.deviceType_isDesktop_correctForAllTypes" --info`
Expected: FAIL — `isDesktop()` method does not exist

- [ ] **Step 3: Write the implementation**

In `DeviceType.java`, add after the `getPriority()` method (after line 23):

```java
/** Desktop devices (priority 0) support Claude Code Agent SDK. */
public boolean isDesktop() {
	return priority == 0;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.DeviceSettingsTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceType.java data/data-social/src/test/java/io/strategiz/social/data/entity/DeviceSettingsTest.java
git commit -m "feat: add isDesktop() helper to DeviceType for Claude Code eligibility"
```

---

### Task 4: Add executionEngine and claudeCodeConfig to DeviceSettings

**Files:**
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceSettings.java:7-19`
- Modify: `data/data-social/src/test/java/io/strategiz/social/data/entity/DeviceSettingsTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `DeviceSettingsTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify they fail**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.DeviceSettingsTest" --info`
Expected: FAIL — `getExecutionEngine()` and `getClaudeCodeConfig()` do not exist

- [ ] **Step 3: Write the implementation**

In `DeviceSettings.java`, add after line 13 (`private int priority = 0;`):

```java
private ExecutionEngine executionEngine = ExecutionEngine.CLAUDE_CODE;

private ClaudeCodeConfig claudeCodeConfig;
```

Add getter/setter methods after the `setPriority` method (after line 43):

```java
public ExecutionEngine getExecutionEngine() {
	return executionEngine;
}

public void setExecutionEngine(ExecutionEngine executionEngine) {
	this.executionEngine = executionEngine;
}

public ClaudeCodeConfig getClaudeCodeConfig() {
	return claudeCodeConfig;
}

public void setClaudeCodeConfig(ClaudeCodeConfig claudeCodeConfig) {
	this.claudeCodeConfig = claudeCodeConfig;
}
```

- [ ] **Step 4: Run test to verify they pass**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.DeviceSettingsTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceSettings.java data/data-social/src/test/java/io/strategiz/social/data/entity/DeviceSettingsTest.java
git commit -m "feat: add executionEngine and claudeCodeConfig fields to DeviceSettings"
```

---

### Task 5: Add claudeCodeVersion to DeviceRegistration

**Files:**
- Modify: `data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceRegistration.java:47-48`
- Modify: `data/data-social/src/test/java/io/strategiz/social/data/entity/DeviceSettingsTest.java`

- [ ] **Step 1: Write the failing test**

Add to `DeviceSettingsTest.java`:

```java
@Test
void deviceRegistration_claudeCodeVersion_getSet() {
	DeviceRegistration device = new DeviceRegistration();
	assertNull(device.getClaudeCodeVersion());
	device.setClaudeCodeVersion("1.0.32");
	assertEquals("1.0.32", device.getClaudeCodeVersion());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.DeviceSettingsTest.deviceRegistration_claudeCodeVersion_getSet" --info`
Expected: FAIL — `getClaudeCodeVersion()` does not exist

- [ ] **Step 3: Write the implementation**

In `DeviceRegistration.java`, add after line 47 (`private String daemonVersion;`):

```java
private String claudeCodeVersion;
```

Add getter/setter after the `setDaemonVersion` method (after line 173):

```java
public String getClaudeCodeVersion() {
	return claudeCodeVersion;
}

public void setClaudeCodeVersion(String claudeCodeVersion) {
	this.claudeCodeVersion = claudeCodeVersion;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:data-social:test --tests "io.strategiz.social.data.entity.DeviceSettingsTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Run all data module tests**

Run: `./gradlew :data:data-social:test --info`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add data/data-social/src/main/java/io/strategiz/social/data/entity/DeviceRegistration.java data/data-social/src/test/java/io/strategiz/social/data/entity/DeviceSettingsTest.java
git commit -m "feat: add claudeCodeVersion field to DeviceRegistration"
```

---

## Chunk 2: Service Layer — REST DTOs, Controller, and WebSocket

### Task 6: Update REST DTOs for Claude Code fields

**Files:**
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/DeviceSettingsResponse.java`
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/UpdateDeviceSettingsRequest.java`

- [ ] **Step 1: Add fields to DeviceSettingsResponse**

After line 14 (`private int priority;`), add:

```java
private String executionEngine;

private Map<String, Object> claudeCodeConfig;

private String claudeCodeVersion;
```

Add import at top:

```java
import java.util.Map;
```

Add getter/setters after the `setPriority` method:

```java
public String getExecutionEngine() {
	return executionEngine;
}

public void setExecutionEngine(String executionEngine) {
	this.executionEngine = executionEngine;
}

public Map<String, Object> getClaudeCodeConfig() {
	return claudeCodeConfig;
}

public void setClaudeCodeConfig(Map<String, Object> claudeCodeConfig) {
	this.claudeCodeConfig = claudeCodeConfig;
}

public String getClaudeCodeVersion() {
	return claudeCodeVersion;
}

public void setClaudeCodeVersion(String claudeCodeVersion) {
	this.claudeCodeVersion = claudeCodeVersion;
}
```

- [ ] **Step 2: Add fields to UpdateDeviceSettingsRequest**

After line 10 (`private Integer priority;`), add:

```java
private String executionEngine;

private Map<String, Object> claudeCodeConfig;
```

Add import at top:

```java
import java.util.Map;
```

Add getter/setters after the `setPriority` method:

```java
public String getExecutionEngine() {
	return executionEngine;
}

public void setExecutionEngine(String executionEngine) {
	this.executionEngine = executionEngine;
}

public Map<String, Object> getClaudeCodeConfig() {
	return claudeCodeConfig;
}

public void setClaudeCodeConfig(Map<String, Object> claudeCodeConfig) {
	this.claudeCodeConfig = claudeCodeConfig;
}
```

- [ ] **Step 3: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/DeviceSettingsResponse.java service/service-agent/src/main/java/io/strategiz/social/service/agent/dto/UpdateDeviceSettingsRequest.java
git commit -m "feat: add Claude Code fields to device settings DTOs"
```

---

### Task 7: Update SettingsController to map new fields

**Files:**
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/SettingsController.java:104-133,188-196`

- [ ] **Step 1: Update the updateDeviceSettings method**

In `SettingsController.java`, after line 127 (`settings.setPriority(request.getPriority());` / `}`), add:

```java
if (request.getExecutionEngine() != null) {
	try {
		settings.setExecutionEngine(ExecutionEngine.valueOf(request.getExecutionEngine().toUpperCase()));
	}
	catch (IllegalArgumentException e) {
		return ResponseEntity.badRequest().build();
	}
}
if (request.getClaudeCodeConfig() != null) {
	ClaudeCodeConfig ccConfig = settings.getClaudeCodeConfig();
	if (ccConfig == null) {
		ccConfig = ClaudeCodeConfig.defaults();
	}
	Map<String, Object> configMap = request.getClaudeCodeConfig();
	if (configMap.containsKey("model")) {
		ccConfig.setModel((String) configMap.get("model"));
	}
	if (configMap.containsKey("maxTurns")) {
		ccConfig.setMaxTurns(((Number) configMap.get("maxTurns")).intValue());
	}
	if (configMap.containsKey("maxBudgetUsd")) {
		ccConfig.setMaxBudgetUsd(new java.math.BigDecimal(configMap.get("maxBudgetUsd").toString()));
	}
	if (configMap.containsKey("permissionMode")) {
		ccConfig.setPermissionMode((String) configMap.get("permissionMode"));
	}
	if (configMap.containsKey("allowedTools")) {
		@SuppressWarnings("unchecked")
		List<String> tools = (List<String>) configMap.get("allowedTools");
		ccConfig.setAllowedTools(tools);
	}
	if (configMap.containsKey("disallowedTools")) {
		@SuppressWarnings("unchecked")
		List<String> tools = (List<String>) configMap.get("disallowedTools");
		ccConfig.setDisallowedTools(tools);
	}
	if (configMap.containsKey("mcpServers")) {
		@SuppressWarnings("unchecked")
		Map<String, Object> servers = (Map<String, Object>) configMap.get("mcpServers");
		ccConfig.setMcpServers(servers);
	}
	if (configMap.containsKey("systemPromptOverride")) {
		ccConfig.setSystemPromptOverride((String) configMap.get("systemPromptOverride"));
	}
	settings.setClaudeCodeConfig(ccConfig);
}
```

Add imports:

```java
import io.strategiz.social.data.entity.ClaudeCodeConfig;
import io.strategiz.social.data.entity.ExecutionEngine;
import java.util.List;
```

- [ ] **Step 2: Update the toDeviceSettingsResponse helper**

Replace the `toDeviceSettingsResponse` method (lines 188-196) with:

```java
private DeviceSettingsResponse toDeviceSettingsResponse(DeviceRegistration device, DeviceSettings settings) {
	DeviceSettingsResponse response = new DeviceSettingsResponse();
	response.setDeviceId(device.getId());
	response.setDeviceName(device.getDeviceName());
	response.setMaxDaemons(settings.getMaxDaemons());
	response.setAutoWake(settings.isAutoWake());
	response.setPriority(settings.getPriority());
	response.setExecutionEngine(
			settings.getExecutionEngine() != null ? settings.getExecutionEngine().name() : null);
	if (settings.getClaudeCodeConfig() != null) {
		ClaudeCodeConfig cc = settings.getClaudeCodeConfig();
		Map<String, Object> ccMap = new java.util.HashMap<>();
		ccMap.put("model", cc.getModel());
		ccMap.put("maxTurns", cc.getMaxTurns());
		ccMap.put("maxBudgetUsd", cc.getMaxBudgetUsd());
		ccMap.put("permissionMode", cc.getPermissionMode());
		if (cc.getAllowedTools() != null) ccMap.put("allowedTools", cc.getAllowedTools());
		if (cc.getDisallowedTools() != null) ccMap.put("disallowedTools", cc.getDisallowedTools());
		if (cc.getMcpServers() != null) ccMap.put("mcpServers", cc.getMcpServers());
		if (cc.getSystemPromptOverride() != null) ccMap.put("systemPromptOverride", cc.getSystemPromptOverride());
		response.setClaudeCodeConfig(ccMap);
	}
	response.setClaudeCodeVersion(device.getClaudeCodeVersion());
	return response;
}
```

- [ ] **Step 3: Verify service module compiles**

Run: `./gradlew :service:service-agent:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/controller/SettingsController.java
git commit -m "feat: update SettingsController to handle Claude Code config fields"
```

---

### Task 8: Handle new WebSocket message types

**Files:**
- Modify: `service/service-agent/src/main/java/io/strategiz/social/service/agent/websocket/DeviceWebSocketHandler.java:85-96`

- [ ] **Step 1: Add new message type handlers**

In the `handleTextMessage` switch statement (line 85-96), add two new cases before the `default` case:

```java
case "claude_code_status" -> handleClaudeCodeStatus(node, principal);
case "engine_selected" -> handleEngineSelected(node, principal);
```

Add the handler methods at the end of the class (before the closing `}`):

```java
private void handleClaudeCodeStatus(JsonNode node, WebSocketPrincipal principal) {
	String version = node.has("version") ? node.get("version").asText() : null;
	boolean available = node.has("available") && node.get("available").asBoolean();
	log.info("[WS] Claude Code status from device {}: version={} available={}",
			principal.getDeviceId(), version, available);
	if (version != null) {
		registryService.updateClaudeCodeVersion(principal.getDeviceId(), principal.getUserId(), version);
	}
}

private void handleEngineSelected(JsonNode node, WebSocketPrincipal principal) {
	String sparkId = node.has("sparkId") ? node.get("sparkId").asText() : null;
	String engine = node.has("engine") ? node.get("engine").asText() : null;
	String reason = node.has("reason") ? node.get("reason").asText() : null;
	log.info("[WS] Engine selected for spark {}: engine={} reason={} device={}",
			sparkId, engine, reason, principal.getDeviceId());
}
```

- [ ] **Step 2: Add updateClaudeCodeVersion to DeviceRegistryService**

Find `DeviceRegistryService.java` and add:

```java
public void updateClaudeCodeVersion(String deviceId, String userId, String version) {
	deviceRepository.findByIdInSubcollection(userId, deviceId).ifPresent(device -> {
		device.setClaudeCodeVersion(version);
		deviceRepository.saveInSubcollection(userId, device, userId);
	});
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :service:service-agent:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add service/service-agent/src/main/java/io/strategiz/social/service/agent/websocket/DeviceWebSocketHandler.java business/business-agent/src/main/java/io/strategiz/social/business/agent/service/DeviceRegistryService.java
git commit -m "feat: handle claude_code_status and engine_selected WebSocket messages"
```

---

## Chunk 3: Business Layer — Agent Skills

### Task 9: Update ManageDeviceSkill for Claude Code config

**Files:**
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/ManageDeviceSkill.java:45-76,127-176`
- Modify: `business/business-agent/src/test/java/io/strategiz/social/business/agent/skill/ManageDeviceSkillTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `ManageDeviceSkillTest.java`:

```java
@Test
void execute_updateSettings_executionEngine_updatesDevice() {
	DeviceRegistration device = new DeviceRegistration();
	device.setId("device-1");
	device.setUserId("user-1");
	device.setDeviceName("My MacBook");
	device.setSettings(DeviceSettings.defaults());
	when(deviceRepository.findByIdInSubcollection("user-1", "device-1")).thenReturn(Optional.of(device));

	ObjectNode input = MAPPER.createObjectNode();
	input.put("action", "update_settings");
	input.put("device_id", "device-1");
	input.put("execution_engine", "LEGACY");

	String result = skill.execute(input, "user-1");

	assertTrue(result.contains("Device settings updated"));
	assertTrue(result.contains("Execution engine: LEGACY"));

	ArgumentCaptor<DeviceRegistration> captor = ArgumentCaptor.forClass(DeviceRegistration.class);
	verify(deviceRepository).saveInSubcollection(eq("user-1"), captor.capture(), eq("user-1"));
	assertEquals(ExecutionEngine.LEGACY, captor.getValue().getSettings().getExecutionEngine());
}

@Test
void execute_updateSettings_invalidEngine_returnsError() {
	DeviceRegistration device = new DeviceRegistration();
	device.setId("device-1");
	device.setUserId("user-1");
	device.setDeviceName("My MacBook");
	device.setSettings(DeviceSettings.defaults());
	when(deviceRepository.findByIdInSubcollection("user-1", "device-1")).thenReturn(Optional.of(device));

	ObjectNode input = MAPPER.createObjectNode();
	input.put("action", "update_settings");
	input.put("device_id", "device-1");
	input.put("execution_engine", "INVALID");

	String result = skill.execute(input, "user-1");

	assertTrue(result.contains("Invalid execution engine"));
}

@Test
void execute_list_showsExecutionEngine() {
	DeviceRegistration device = new DeviceRegistration();
	device.setId("device-1");
	device.setDeviceName("My MacBook");
	device.setDeviceType(DeviceType.MACOS);
	device.setState(DeviceState.ACTIVE);
	DeviceSettings settings = new DeviceSettings();
	settings.setExecutionEngine(ExecutionEngine.CLAUDE_CODE);
	device.setSettings(settings);
	when(deviceRegistryService.listDevices("user-1")).thenReturn(List.of(device));

	ObjectNode input = MAPPER.createObjectNode();
	input.put("action", "list");

	String result = skill.execute(input, "user-1");

	assertTrue(result.contains("engine=CLAUDE_CODE"));
}
```

Add import to the test file:

```java
import io.strategiz.social.data.entity.ExecutionEngine;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :business:business-agent:test --tests "io.strategiz.social.business.agent.skill.ManageDeviceSkillTest" --info`
Expected: FAIL — assertions fail

- [ ] **Step 3: Update the tool definition**

In `ManageDeviceSkill.java`, update `getDescription()` (line 46):

```java
return "Pair a new device, unpair a device, or update device settings (max daemons, auto wake, priority, execution engine, Claude Code config)";
```

In `getToolDefinition()`, add after the `priority` property definition (after line 75):

```java
ObjectNode executionEngine = properties.putObject("execution_engine");
executionEngine.put("type", "string");
executionEngine.put("description",
		"Execution engine for sparks: CLAUDE_CODE (Agent SDK, desktop default), LEGACY (command-based), AUTO (choose per-spark). For update_settings on desktop devices.");
executionEngine.putArray("enum").add("CLAUDE_CODE").add("LEGACY").add("AUTO");
```

- [ ] **Step 4: Update handleUpdateSettings()**

In `handleUpdateSettings()`, add after the priority block (after line 157):

```java
if (input.has("execution_engine")) {
	String engineStr = input.get("execution_engine").asText();
	try {
		settings.setExecutionEngine(ExecutionEngine.valueOf(engineStr.toUpperCase()));
		updated = true;
	}
	catch (IllegalArgumentException e) {
		return "Invalid execution engine: " + engineStr + ". Use CLAUDE_CODE, LEGACY, or AUTO.";
	}
}
```

Add import:

```java
import io.strategiz.social.data.entity.ExecutionEngine;
```

- [ ] **Step 5: Update the response message**

Replace the return statement in `handleUpdateSettings` (lines 167-170) with:

```java
return "Device settings updated for " + device.getDeviceName() + ":\n"
		+ "- Max daemons: " + settings.getMaxDaemons() + "\n"
		+ "- Auto wake: " + settings.isAutoWake() + "\n"
		+ "- Priority: " + settings.getPriority() + "\n"
		+ "- Execution engine: " + settings.getExecutionEngine();
```

- [ ] **Step 6: Update the "no fields" validation**

Replace the "No settings fields" message (line 160) with:

```java
return "No settings fields provided. Specify at least one of: max_daemons, auto_wake, priority, execution_engine.";
```

- [ ] **Step 7: Update handleList() to show engine**

In `handleList()`, replace the settings display block (lines 191-196) with:

```java
if (device.getSettings() != null) {
	DeviceSettings s = device.getSettings();
	sb.append(", max_daemons=").append(s.getMaxDaemons())
			.append(", auto_wake=").append(s.isAutoWake())
			.append(", priority=").append(s.getPriority())
			.append(", engine=").append(s.getExecutionEngine());
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :business:business-agent:test --tests "io.strategiz.social.business.agent.skill.ManageDeviceSkillTest" --info`
Expected: ALL PASS

- [ ] **Step 9: Commit**

```bash
git add business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/ManageDeviceSkill.java business/business-agent/src/test/java/io/strategiz/social/business/agent/skill/ManageDeviceSkillTest.java
git commit -m "feat: add execution engine and Claude Code config to ManageDeviceSkill"
```

---

### Task 10: Update ConnectionStatusSkill to show engine info

**Files:**
- Modify: `business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/ConnectionStatusSkill.java`
- Modify: `business/business-agent/src/test/java/io/strategiz/social/business/agent/skill/ConnectionStatusSkillTest.java`

- [ ] **Step 1: Write the failing test**

Add to `ConnectionStatusSkillTest.java`:

```java
@Test
void execute_desktopDevice_showsExecutionEngine() {
	DeviceRegistration device = new DeviceRegistration();
	device.setId("device-1");
	device.setDeviceName("My MacBook");
	device.setDeviceType(DeviceType.MACOS);
	DeviceSettings settings = new DeviceSettings();
	settings.setExecutionEngine(ExecutionEngine.CLAUDE_CODE);
	device.setSettings(settings);
	device.setClaudeCodeVersion("1.0.32");
	when(deviceRepository.findActiveByUserId("user-1")).thenReturn(List.of(device));

	DeviceSession session = new DeviceSession();
	session.setDeviceId("device-1");
	when(sessionRepository.findActiveByUserId("user-1")).thenReturn(List.of(session));
	when(integrationRepository.findAllByUserId("user-1")).thenReturn(Collections.emptyList());
	when(repoGrantRepository.findActiveByUserId("user-1")).thenReturn(Collections.emptyList());

	ObjectNode input = MAPPER.createObjectNode();
	String result = skill.execute(input, "user-1");

	assertTrue(result.contains("CLAUDE_CODE"));
	assertTrue(result.contains("v1.0.32"));
}
```

Add imports to test file:

```java
import io.strategiz.social.data.entity.DeviceSettings;
import io.strategiz.social.data.entity.ExecutionEngine;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :business:business-agent:test --tests "io.strategiz.social.business.agent.skill.ConnectionStatusSkillTest.execute_desktopDevice_showsExecutionEngine" --info`
Expected: FAIL

- [ ] **Step 3: Update the appendDevices method**

In `ConnectionStatusSkill.java`, find the device display section in `appendDevices()` and add engine info for desktop devices. After the online/offline status line, add:

```java
if (device.getDeviceType() != null && device.getDeviceType().isDesktop()) {
	String engine = device.getSettings() != null && device.getSettings().getExecutionEngine() != null
			? device.getSettings().getExecutionEngine().name() : "CLAUDE_CODE";
	sb.append(", engine: ").append(engine);
	if (device.getClaudeCodeVersion() != null) {
		sb.append(" (v").append(device.getClaudeCodeVersion()).append(")");
	}
}
```

- [ ] **Step 4: Run all ConnectionStatusSkill tests**

Run: `./gradlew :business:business-agent:test --tests "io.strategiz.social.business.agent.skill.ConnectionStatusSkillTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add business/business-agent/src/main/java/io/strategiz/social/business/agent/skill/ConnectionStatusSkill.java business/business-agent/src/test/java/io/strategiz/social/business/agent/skill/ConnectionStatusSkillTest.java
git commit -m "feat: show execution engine info in ConnectionStatusSkill for desktop devices"
```

---

## Chunk 4: Final Integration and Verification

### Task 11: Run full build and verify

**Files:** None (verification only)

- [ ] **Step 1: Run all tests across all modules**

Run: `./gradlew test --info`
Expected: ALL PASS

- [ ] **Step 2: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify no compilation warnings related to new code**

Run: `./gradlew compileJava 2>&1 | grep -i "warning\|error" | head -20`
Expected: No new warnings

- [ ] **Step 4: Final commit if any formatting/cleanup needed**

```bash
git status
```

If clean, no action needed. If there are formatting changes from the build, commit them:

```bash
git add -A
git commit -m "chore: formatting cleanup after Claude Code engine changes"
```
