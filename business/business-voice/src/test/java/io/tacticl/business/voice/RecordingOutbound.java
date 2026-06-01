package io.tacticl.business.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test double for {@link VoiceOutbound} that records everything sent so
 * assertions can inspect the emitted frames and audio without a real socket.
 */
class RecordingOutbound implements VoiceOutbound {

    final List<byte[]> audio = new ArrayList<>();

    final List<Map<String, Object>> controls = new ArrayList<>();

    @Override
    public void sendAudio(byte[] data) {
        audio.add(data);
    }

    @Override
    public void sendControl(Map<String, Object> frame) {
        controls.add(frame);
    }

    /** All control frames whose {@code type} equals the given value. */
    List<Map<String, Object>> framesOfType(String type) {
        return controls.stream().filter(f -> type.equals(f.get("type"))).toList();
    }

    /** The last {@code state} frame's state token, if any. */
    Optional<String> lastState() {
        List<Map<String, Object>> states = framesOfType("state");
        return states.isEmpty() ? Optional.empty()
            : Optional.ofNullable((String) states.get(states.size() - 1).get("state"));
    }
}
