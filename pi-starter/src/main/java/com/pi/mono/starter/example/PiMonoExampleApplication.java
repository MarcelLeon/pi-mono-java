package com.pi.mono.starter.example;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.MessageRole;
import com.pi.mono.session.SessionManager;
import com.pi.mono.llm.LLMProviderManager;
import com.pi.mono.tools.ToolManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Pi-Mono Java Starter 示例应用
 *
 * 展示如何在Spring Boot应用中集成Pi-Mono Java
 */
@SpringBootApplication
@EnableConfigurationProperties
public class PiMonoExampleApplication implements CommandLineRunner {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private LLMProviderManager llmProviderManager;

    @Autowired
    private ToolManager toolManager;

    public static void main(String[] args) {
        SpringApplication.run(PiMonoExampleApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🚀 Pi-Mono Java Example Application Starting...");

        // 创建会话
        String sessionId = sessionManager.createSession("mock-claude");
        System.out.println("✅ Created session: " + sessionId);

        // 显示可用的LLM提供商
        System.out.println("\n🤖 Available LLM Providers:");
        llmProviderManager.getAllProviders().forEach(provider -> {
            System.out.println("  - " + provider.getId() + ": " + provider.health());
        });

        // 显示可用工具
        System.out.println("\n🔧 Available Tools:");
        toolManager.getAllTools().forEach((name, tool) -> {
            System.out.println("  - " + name + ": " + tool.getDescription());
        });

        // 发送消息示例
        try {
            System.out.println("\n💬 Sending message to AI...");
            CompletableFuture<AgentMessage> response = sessionManager.sendMessage("What is Java programming language?");

            AgentMessage result = response.get();
            System.out.println("👤 You: What is Java programming language?");
            System.out.println("🤖 Pi: " + result.content());

            // 显示会话历史
            System.out.println("\n📂 Session History:");
            sessionManager.getSessionHistory().forEach(node -> {
                System.out.println("  " + node.message().role() + ": " + node.message().content());
            });

        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }

        // 工具使用示例
        System.out.println("\n🛠️  Tool Usage Examples:");
        System.out.println("You can use tools like this:");
        System.out.println("  toolManager.executeTool(\"ls\", Map.of(\"path\", \".\"), sessionId, null)");
        System.out.println("  toolManager.executeTool(\"read\", Map.of(\"path\", \"README.md\"), sessionId, null)");

        System.out.println("\n🎉 Example application completed successfully!");
        System.out.println("Your Spring Boot application is now integrated with Pi-Mono Java!");
    }
}