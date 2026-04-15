package io.tacticl.data.connections.entity;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DeviceTest {

    @Test
    void create_setsOfflineStatus() {
        var device = Device.create("user1", "My Mac", "MACOS", "1.4.2", List.of("CLAUDE_CODE"));
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.OFFLINE);
        assertThat(device.getName()).isEqualTo("My Mac");
        assertThat(device.getOs()).isEqualTo("MACOS");
        assertThat(device.getSettings()).isNotNull();
    }

    @Test
    void markOnline_setsOnlineStatusAndLastSeenAt() {
        var device = Device.create("user1", "My Mac", "MACOS", "1.4.2", List.of("CLAUDE_CODE"));
        device.markOnline();
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(device.getLastSeenAt()).isNotNull();
    }

    @Test
    void markOffline_setsOfflineStatus() {
        var device = Device.create("user1", "My Mac", "MACOS", "1.4.2", List.of("CLAUDE_CODE"));
        device.markOnline();
        device.markOffline();
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.OFFLINE);
    }

    @Test
    void deviceSettings_defaultValues() {
        var device = Device.create("user1", "My Mac", "MACOS", "1.4.2", List.of("CLAUDE_CODE"));
        var settings = device.getSettings();
        assertThat(settings.getExecutionEngine()).isEqualTo("AUTO");
        assertThat(settings.getMaxDaemons()).isEqualTo(3);
        assertThat(settings.isAutoWake()).isFalse();
    }
}
