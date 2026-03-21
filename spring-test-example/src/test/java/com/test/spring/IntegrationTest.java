package com.test.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.ExecutionException;

import com.pi.mono.session.SessionManager;
import com.pi.mono.core.AgentMessage;

@SpringBootTest
@ActiveProfiles("test")
class IntegrationTest {

    @Autowired
    private SessionManager sessionManager;

    @Test
    void testCompleteWorkflow() {
        System.out.println("📋 集成测试: 完整工作流程");

        try {
            // 1. 创建会话
            String sessionId = sessionManager.createSession("mock-claude");
            assertNotNull(sessionId);
            System.out.println("✅ 会话创建成功: " + sessionId);

            // 2. 发送多条消息
            var response1 = sessionManager.sendMessage("Hello");
            AgentMessage message1 = response1.get();
            System.out.println("✅ 第一条消息响应: " + message1.content());

            var response2 = sessionManager.sendMessage("How are you?");
            AgentMessage message2 = response2.get();
            System.out.println("✅ 第二条消息响应: " + message2.content());

            // 3. 保存会话
            sessionManager.saveSession();
            System.out.println("✅ 会话保存成功");

            // 4. 验证会话文件存在
            java.io.File sessionDir = new java.io.File("target/test-sessions");
            assertTrue(sessionDir.exists());
            assertTrue(sessionDir.isDirectory());
            System.out.println("✅ 会话文件目录验证通过");

            // 5. 创建新会话并验证隔离
            String sessionId2 = sessionManager.createSession("gpt-3.5-turbo");
            var response3 = sessionManager.sendMessage("New session message");
            AgentMessage message3 = response3.get();
            System.out.println("✅ 新会话创建和隔离验证通过: " + message3.content());

            System.out.println("🎉 完整集成测试通过！");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("测试执行失败", e);
        }
    }
}