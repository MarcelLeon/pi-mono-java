package com.test.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pi.mono.session.SessionManager;
import com.pi.mono.core.AgentMessage;

@SpringBootApplication
public class SpringTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringTestApplication.class, args);
    }
}

@Component
@Profile("dev")
class PiMonoTestRunner implements CommandLineRunner {

    @Autowired
    private SessionManager sessionManager;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🚀 Pi-Mono Java Spring集成测试开始");

        try {
            // 测试1: 创建会话
            System.out.println("📋 测试1: 创建会话");
            String sessionId = sessionManager.createSession("mock-claude");
            System.out.println("✅ 会话创建成功: " + sessionId);

            // 测试2: 发送消息
            System.out.println("💬 测试2: 发送消息");
            var response = sessionManager.sendMessage("Hello, Pi-Mono Java!");
            AgentMessage message = response.get();
            System.out.println("✅ AI响应: " + message.content());
            System.out.println("   角色: " + message.role());
            System.out.println("   元数据: " + message.metadata());

            // 测试3: 保存会话
            System.out.println("💾 测试3: 保存会话");
            sessionManager.saveSession();
            System.out.println("✅ 会话保存成功");

            // 测试4: 获取会话列表
            System.out.println("📋 测试4: 获取会话列表");
            var sessions = sessionManager.listSessions();
            System.out.println("✅ 会话列表: " + sessions);

            // 测试5: 测试多个会话
            System.out.println("📋 测试5: 测试多个会话");
            String sessionId2 = sessionManager.createSession("gpt-3.5-turbo");
            var response2 = sessionManager.sendMessage("How are you today?");
            AgentMessage message2 = response2.get();
            System.out.println("✅ 第二个会话响应: " + message2.content());

            // 测试6: 会话切换
            System.out.println("📋 测试6: 会话切换");
            // 重新加载第一个会话
            sessionManager.loadSession(sessionId);
            var response3 = sessionManager.sendMessage("What did we talk about before?");
            AgentMessage message3 = response3.get();
            System.out.println("✅ 切换会话响应: " + message3.content());

            System.out.println("🎉 所有Spring集成测试通过！");

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalStateException("Spring integration smoke run failed", e);
        }
    }
}
