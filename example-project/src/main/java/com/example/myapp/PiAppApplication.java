package com.example.myapp;

import com.pi.mono.core.AgentSession;
import com.pi.mono.session.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PiAppApplication implements CommandLineRunner {

    @Autowired
    private SessionManager sessionManager;

    public static void main(String[] args) {
        SpringApplication.run(PiAppApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 创建会话
        String sessionId = sessionManager.createSession("mock-claude");
        System.out.println("会话创建成功: " + sessionId);

        // 发送消息
        var futureResponse = sessionManager.sendMessage("Hello, Pi-Mono Java!");
        var response = futureResponse.get();
        System.out.println("AI响应: " + response.content());

        // 保存会话
        sessionManager.saveSession();
        System.out.println("会话已保存");
    }
}