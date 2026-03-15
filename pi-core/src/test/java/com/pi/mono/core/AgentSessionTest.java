package com.pi.mono.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AgentSessionTest {

    @Test
    public void testAgentMessageCreation() {
        // Given
        String content = "Hello, Pi!";
        AgentMessage message = new AgentMessage(
            MessageRole.USER,
            content,
            Map.of("timestamp", System.currentTimeMillis())
        );

        // Then
        assertEquals(MessageRole.USER, message.role());
        assertEquals(content, message.content());
        assertNotNull(message.metadata());
    }

    @Test
    public void testSessionNodeCreation() {
        // Given
        AgentMessage message = new AgentMessage(
            MessageRole.ASSISTANT,
            "Hello!",
            Map.of()
        );

        SessionNode node = new SessionNode(
            "ulid-123",
            null,
            message,
            LocalDateTime.now(),
            Map.of(),
            100,
            1,
            Optional.empty()
        );

        // Then
        assertEquals("ulid-123", node.id());
        assertNull(node.parentId());
        assertEquals(message, node.message());
        assertEquals(100, node.tokenUsage());
    }
}