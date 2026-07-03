package com.pi.mono.session;

import java.util.Map;

/**
 * Minimal Java-side source for upstream-style session_info_changed notifications.
 */
public record SessionInfoChangedEvent(
    String eventType,
    String sessionId,
    String rootNodeId,
    String sessionName,
    Map<String, Object> metadata
) {
    public SessionInfoChangedEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
