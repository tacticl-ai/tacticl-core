package io.tacticl.data.connections.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("devices")
public class Device extends BaseMongoEntity {

    @Indexed
    private String userId;
    private String name;
    private String os;
    private String agentVersion;
    private List<String> capabilities;
    private DeviceStatus status;
    private Instant lastSeenAt;
    private DeviceSettings settings;

    public static class DeviceSettings {
        private String executionEngine = "AUTO";
        private int maxDaemons = 3;
        private boolean autoWake = false;

        public String getExecutionEngine() { return executionEngine; }
        public void setExecutionEngine(String executionEngine) { this.executionEngine = executionEngine; }
        public int getMaxDaemons() { return maxDaemons; }
        public void setMaxDaemons(int maxDaemons) { this.maxDaemons = maxDaemons; }
        public boolean isAutoWake() { return autoWake; }
        public void setAutoWake(boolean autoWake) { this.autoWake = autoWake; }
    }

    public static Device create(String userId, String name, String os,
                                String agentVersion, List<String> capabilities) {
        var d = new Device();
        d.userId = userId;
        d.name = name;
        d.os = os;
        d.agentVersion = agentVersion;
        d.capabilities = capabilities;
        d.status = DeviceStatus.OFFLINE;
        d.settings = new DeviceSettings();
        return d;
    }

    public void markOnline() {
        this.status = DeviceStatus.ONLINE;
        this.lastSeenAt = Instant.now();
    }

    public void markOffline() {
        this.status = DeviceStatus.OFFLINE;
    }

    public void setName(String name) { this.name = name; }
    public void setSettings(DeviceSettings settings) { this.settings = settings; }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getOs() { return os; }
    public String getAgentVersion() { return agentVersion; }
    public List<String> getCapabilities() { return capabilities; }
    public DeviceStatus getStatus() { return status; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public DeviceSettings getSettings() { return settings; }
}
