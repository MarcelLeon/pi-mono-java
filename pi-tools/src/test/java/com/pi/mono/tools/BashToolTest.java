package com.pi.mono.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BashToolTest {

    private final BashTool bashTool = new BashTool();

    @Test
    void rejectsNonPositiveTimeouts() {
        ToolExecutionResult zeroTimeout = executeWithTimeout(0);
        ToolExecutionResult negativeTimeout = executeWithTimeout(-1);

        assertFalse(zeroTimeout.success());
        assertTrue(zeroTimeout.content().contains("timeout must be between 1 and 60 seconds"));
        assertFalse(negativeTimeout.success());
        assertTrue(negativeTimeout.content().contains("timeout must be between 1 and 60 seconds"));
    }

    @Test
    void rejectsOversizedTimeoutsInsteadOfClamping() {
        ToolExecutionResult result = executeWithTimeout(61);

        assertFalse(result.success());
        assertTrue(result.content().contains("timeout must be between 1 and 60 seconds"));
    }

    @Test
    void preservesAcceptedTimeoutInMetadata() {
        ToolExecutionResult result = executeWithTimeout(5);

        assertTrue(result.success());
        assertEquals(5, result.metadata().get("timeout"));
    }

    private ToolExecutionResult executeWithTimeout(int timeout) {
        return bashTool.execute(new ToolExecutionRequest(
            "bash",
            Map.of("command", "echo ok", "timeout", timeout),
            "session-id",
            "node-id"
        )).join();
    }
}
