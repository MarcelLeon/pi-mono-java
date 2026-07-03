package com.pi.mono.session;

/**
 * Listener for session metadata events surfaced to CLI/RPC adapters.
 */
@FunctionalInterface
public interface SessionEventListener {
    void onSessionInfoChanged(SessionInfoChangedEvent event);
}
