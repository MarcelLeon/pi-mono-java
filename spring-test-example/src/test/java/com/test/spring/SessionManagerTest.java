package com.test.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.pi.mono.session.SessionManager;
import com.pi.mono.core.AgentMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;

@SpringBootTest
@ActiveProfiles("test")
class SessionManagerTest {

    @Autowired
    private SessionManager sessionManager;

    @Test
    void testCreateSession() {
        String sessionId = sessionManager.createSession("mock-claude");
        assertNotNull(sessionId);
        assertTrue(sessionId.length() > 0);
        System.out.println("✅ 会话创建测试通过: " + sessionId);
    }

    @Test
    void testSendMessage() {
        String sessionId = sessionManager.createSession("mock-claude");
        var response = sessionManager.sendMessage("Test message");

        try {
            AgentMessage message = response.get();
            assertEquals("ASSISTANT", message.role().name());
            assertNotNull(message.content());
            System.out.println("✅ 消息发送测试通过: " + message.content());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("消息发送测试失败", e);
        }
    }

    @Test
    void testSaveSession() {
        sessionManager.createSession("mock-claude");
        sessionManager.sendMessage("Test message");

        // 保存会话不应该抛出异常
        assertDoesNotThrow(() -> sessionManager.saveSession());
        System.out.println("✅ 会话保存测试通过");
    }

    @Test
    void testListSessions() {
        // 创建多个会话
        sessionManager.createSession("mock-claude");
        sessionManager.saveSession();
        sessionManager.createSession("gpt-3.5-turbo");
        sessionManager.saveSession();

        var sessions = sessionManager.listSessions();
        assertNotNull(sessions);
        assertTrue(sessions.size() >= 2);
        System.out.println("✅ 会话列表测试通过: " + sessions.size() + " 个会话");
    }

    @Test
    void testMultipleSessions() {
        try {
            // 创建第一个会话
            String sessionId1 = sessionManager.createSession("mock-claude");
            var response1 = sessionManager.sendMessage("Hello from session 1");

            // 创建第二个会话
            String sessionId2 = sessionManager.createSession("gpt-3.5-turbo");
            var response2 = sessionManager.sendMessage("Hello from session 2");

            // 验证两个会话都有响应
            AgentMessage message1 = response1.get();
            AgentMessage message2 = response2.get();

            // 验证两个会话ID不同且响应非空
            assertNotEquals(sessionId1, sessionId2);
            assertNotNull(message1.content());
            assertNotNull(message2.content());

            System.out.println("✅ 多会话测试通过");
            System.out.println("   会话1: " + message1.content());
            System.out.println("   会话2: " + message2.content());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("多会话测试失败", e);
        }
    }
}
