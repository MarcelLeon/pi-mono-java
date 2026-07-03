package com.pi.mono.cli;

import java.util.Optional;

public record PiCliStartupOptions(
    boolean rpcMode,
    boolean ephemeralSession,
    Optional<String> sessionId
) {
    public PiCliStartupOptions {
        sessionId = sessionId == null ? Optional.empty() : sessionId.filter(value -> !value.isBlank());
    }

    public static PiCliStartupOptions parse(String... args) {
        boolean rpcMode = false;
        boolean ephemeralSession = false;
        String sessionId = null;

        for (int i = 0; args != null && i < args.length; i++) {
            String arg = args[i];
            if ("--rpc".equals(arg)) {
                rpcMode = true;
            } else if ("--no-session".equals(arg)) {
                ephemeralSession = true;
            } else if ("--session-id".equals(arg)) {
                if (i + 1 >= args.length || args[i + 1].isBlank()) {
                    throw new IllegalArgumentException("--session-id requires a non-blank value");
                }
                sessionId = args[++i];
            } else if (arg.startsWith("--session-id=")) {
                sessionId = arg.substring("--session-id=".length());
                if (sessionId.isBlank()) {
                    throw new IllegalArgumentException("--session-id requires a non-blank value");
                }
            }
        }

        return new PiCliStartupOptions(rpcMode, ephemeralSession, Optional.ofNullable(sessionId));
    }
}
