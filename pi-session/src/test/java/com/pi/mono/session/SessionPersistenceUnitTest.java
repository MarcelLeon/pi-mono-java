package com.pi.mono.session;

import com.pi.mono.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class SessionPersistenceUnitTest {

    private SessionManager sessionManager;
    private SessionPersistence sessionPersistence;

    @BeforeEach
    void setUp() {
        // 创建测试实例
        sessionManager = new SessionManager();

        // 创建SessionPersistence实例并设置sessionDir
        sessionPersistence = new SessionPersistence();
        sessionPersistence.setSessionDir("target/test-sessions");

        // 清理测试目录
        try {
            Path testDir = Paths.get("target/test-sessions");
            if (Files.exists(testDir)) {
                Files.list(testDir)
                    .filter(path -> path.toString().endsWith(".jsonl"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            // 忽略清理错误
                        }
                    });
            }
        } catch (Exception e) {
            // 忽略清理错误
        }
    }

    @Test
    void testSessionPersistenceDirect() {
        System.out.println("🧪 测试会话持久化直接调用...");

        try {
            // 创建会话树
            SessionTree sessionTree = new SessionTree();

            // 创建根节点
            AgentMessage rootMessage = new AgentMessage(
                MessageRole.USER,
                "Test message for save",
                new java.util.HashMap<>()
            );

            SessionNode rootNode = sessionTree.createRoot("test-session", rootMessage);

            // 添加一个子节点
            AgentMessage childMessage = new AgentMessage(
                MessageRole.ASSISTANT,
                "This is a test response",
                new java.util.HashMap<>()
            );

            SessionNode childNode = sessionTree.createBranch(rootNode.id(), childMessage);

            System.out.println("✅ 会话树创建成功");
            System.out.println("  根节点: " + rootNode.id());
            System.out.println("  子节点: " + childNode.id());

            // 保存会话树
            System.out.println("尝试保存会话树...");
            sessionPersistence.saveSessionTree("test-session", sessionTree);
            System.out.println("✅ 会话树保存成功");

            // 验证文件存在
            boolean exists = sessionPersistence.sessionExists("test-session");
            assertTrue(exists, "会话文件应该存在");
            System.out.println("✅ 会话文件存在");

            // 列出所有会话
            List<String> sessions = sessionPersistence.listSessions();
            assertTrue(sessions.contains("test-session"), "会话列表中应该包含新会话");
            System.out.println("✅ 会话列表中包含新会话: " + sessions);

            // 检查文件内容
            Path sessionFile = Paths.get("target/test-sessions", "test-session.jsonl");
            assertTrue(Files.exists(sessionFile), "会话文件应该存在");

            List<String> lines = Files.readAllLines(sessionFile);
            assertFalse(lines.isEmpty(), "会话文件不应该为空");

            System.out.println("会话文件内容:");
            for (String line : lines) {
                System.out.println("  " + line);
            }

            // 验证JSON格式
            for (String line : lines) {
                try {
                    // 简单验证JSON格式
                    assertTrue(line.startsWith("{"), "每行应该是JSON对象");
                    assertTrue(line.endsWith("}"), "每行应该是JSON对象");
                    assertTrue(line.contains("\"id\""), "应该包含id字段");
                    assertTrue(line.contains("\"message\""), "应该包含message字段");
                } catch (Exception e) {
                    fail("JSON格式错误: " + line + " - " + e.getMessage());
                }
            }

            System.out.println("✅ 会话持久化功能测试通过");

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("会话持久化测试失败: " + e.getMessage());
        }
    }
}