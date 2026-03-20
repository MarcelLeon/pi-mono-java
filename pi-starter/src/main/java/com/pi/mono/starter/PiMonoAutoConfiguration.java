package com.pi.mono.starter;

import com.pi.mono.core.AgentSession;
import com.pi.mono.core.LLMProvider;
import com.pi.mono.core.SessionNode;
import com.pi.mono.llm.LLMProviderManager;
import com.pi.mono.session.SessionManager;
import com.pi.mono.session.SessionTree;
import com.pi.mono.session.SessionPersistence;
import com.pi.mono.tools.ToolDefinition;
import com.pi.mono.tools.ToolManager;
import com.pi.mono.tools.ToolPermissionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;

/**
 * Pi-Mono Java Spring Boot 自动配置
 */
@Configuration
@EnableConfigurationProperties(PiMonoProperties.class)
@ComponentScan(basePackages = {
    "com.pi.mono.core",
    "com.pi.mono.llm",
    "com.pi.mono.session",
    "com.pi.mono.tools",
    "com.pi.mono.web"
})
public class PiMonoAutoConfiguration {

    /**
     * 配置SessionManager
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(SessionTree sessionTree, SessionPersistence sessionPersistence) {
        SessionManager manager = new SessionManager();
        // 由于SessionManager使用@Autowired，我们需要确保依赖被注入
        return manager;
    }

    /**
     * 配置SessionTree
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionTree sessionTree() {
        return new SessionTree();
    }

    /**
     * 配置SessionPersistence
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionPersistence sessionPersistence() {
        return new SessionPersistence();
    }

    /**
     * 配置LLMProviderManager
     */
    @Bean
    @ConditionalOnMissingBean
    public LLMProviderManager llmProviderManager(List<LLMProvider> providers) {
        return new LLMProviderManager(providers);
    }

    /**
     * 配置ToolManager
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolManager toolManager(List<ToolDefinition> toolDefinitions) {
        return new ToolManager(toolDefinitions);
    }

    /**
     * 配置ToolPermissionManager
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolPermissionManager toolPermissionManager() {
        return new ToolPermissionManager();
    }
}