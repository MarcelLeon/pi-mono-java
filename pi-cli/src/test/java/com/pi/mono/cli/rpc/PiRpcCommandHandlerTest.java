package com.pi.mono.cli.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.session.SessionManager;
import com.pi.mono.session.SessionPersistence;
import com.pi.mono.session.SessionTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiRpcCommandHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SessionManager sessionManager;
    private PiRpcCommandHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        SessionPersistence persistence = new SessionPersistence();
        persistence.setSessionDir(tempDir.resolve("sessions").toString());

        sessionManager = new SessionManager();
        ReflectionTestUtils.setField(sessionManager, "sessionTree", new SessionTree());
        ReflectionTestUtils.setField(sessionManager, "sessionPersistence", persistence);
        ReflectionTestUtils.setField(sessionManager, "defaultModel", "mock-claude");
        handler = new PiRpcCommandHandler(sessionManager);
    }

    @Test
    void getEntriesReturnsOrderedCurrentSessionEntries() throws Exception {
        String sessionId = createConversation();

        JsonNode response = handle("{\"id\":1,\"method\":\"get_entries\"}");

        JsonNode result = response.get("result");
        assertEquals(1, response.get("id").asInt());
        assertEquals(sessionId, result.get("sessionId").asText());
        assertNotNull(result.get("currentBranchId").asText());
        assertEquals(3, result.get("entries").size());
        assertEquals("SYSTEM", result.get("entries").get(0).get("role").asText());
        assertEquals("USER", result.get("entries").get(1).get("role").asText());
        assertEquals("hello rpc", result.get("entries").get(1).get("content").asText());
        assertNotNull(result.get("entries").get(1).get("rendered"));
        assertEquals("User", result.get("entries").get(1).get("rendered").get("title").asText());
        assertEquals("hello rpc", result.get("entries").get(1).get("rendered").get("plainText").asText());
        assertEquals("### User\n\nhello rpc", result.get("entries").get(1).get("rendered").get("markdown").asText());
        assertEquals("ASSISTANT", result.get("entries").get(2).get("role").asText());
        assertNotNull(result.get("entries").get(2).get("rendered"));
        assertEquals("Assistant", result.get("entries").get(2).get("rendered").get("title").asText());
    }

    @Test
    void getTreeReturnsNodesWithChildren() throws Exception {
        createConversation();

        JsonNode response = handle("{\"id\":\"tree-1\",\"method\":\"get_tree\"}");

        JsonNode result = response.get("result");
        assertEquals("tree-1", response.get("id").asText());
        assertNotNull(result.get("rootId").asText());
        assertNotNull(result.get("currentBranchId").asText());
        assertEquals(3, result.get("nodes").size());
        assertEquals(1, result.get("nodes").get(0).get("children").size());
        assertEquals(result.get("nodes").get(1).get("id").asText(), result.get("nodes").get(0).get("children").get(0).asText());
        assertEquals("USER", result.get("nodes").get(1).get("role").asText());
        assertEquals("ASSISTANT", result.get("nodes").get(2).get("role").asText());
    }

    @Test
    void unknownMethodReturnsErrorEnvelope() throws Exception {
        sessionManager.createSession("mock-claude");

        JsonNode response = handle("{\"id\":2,\"method\":\"unknown\"}");

        assertEquals(2, response.get("id").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Unsupported RPC method"));
    }

    private String createConversation() {
        String sessionId = sessionManager.createSession("mock-claude");
        sessionManager.sendMessage("hello rpc").join();
        return sessionId;
    }

    private JsonNode handle(String request) throws Exception {
        return objectMapper.readTree(handler.handle(request));
    }
}
