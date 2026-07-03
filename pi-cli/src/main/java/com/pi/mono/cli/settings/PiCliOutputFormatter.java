package com.pi.mono.cli.settings;

public class PiCliOutputFormatter {
    private final String padding;

    public PiCliOutputFormatter(PiCliSettings settings) {
        this.padding = " ".repeat(Math.max(0, settings.outputPad()));
    }

    public String userMessage(String content) {
        return padding + "👤 You: " + content;
    }

    public String assistantMessage(String content) {
        return padding + "🤖 Pi: " + content;
    }
}
