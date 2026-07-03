package com.pi.mono.cli.settings;

import java.util.Optional;

public record PiCliSettings(int outputPad, Optional<String> externalEditor) {

    public PiCliSettings {
        outputPad = Math.max(0, outputPad);
        externalEditor = externalEditor == null ? Optional.empty() : externalEditor.filter(value -> !value.isBlank());
    }

    public static PiCliSettings defaults() {
        return new PiCliSettings(0, Optional.empty());
    }
}
