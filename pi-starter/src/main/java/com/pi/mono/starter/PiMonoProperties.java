package com.pi.mono.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Pi-Mono Java 配置属性
 */
@ConfigurationProperties(prefix = "pi.mono")
public class PiMonoProperties {

    /**
     * 默认LLM模型
     */
    private String defaultModel = "mock-claude";

    /**
     * 会话配置
     */
    private Session session = new Session();

    /**
     * LLM提供商配置
     */
    private Map<String, Provider> providers;

    /**
     * 工具配置
     */
    private Tools tools = new Tools();

    /**
     * Web API配置
     */
    private Web web = new Web();

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }

    public Tools getTools() {
        return tools;
    }

    public void setTools(Tools tools) {
        this.tools = tools;
    }

    public Web getWeb() {
        return web;
    }

    public void setWeb(Web web) {
        this.web = web;
    }

    /**
     * 会话配置
     */
    public static class Session {
        /**
         * 会话超时时间
         */
        private Duration timeout = Duration.ofHours(24);

        /**
         * 最大会话数
         */
        private int maxSessions = 1000;

        /**
         * 会话存储路径
         */
        private String storagePath = "./pi-sessions";

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public int getMaxSessions() {
            return maxSessions;
        }

        public void setMaxSessions(int maxSessions) {
            this.maxSessions = maxSessions;
        }

        public String getStoragePath() {
            return storagePath;
        }

        public void setStoragePath(String storagePath) {
            this.storagePath = storagePath;
        }
    }

    /**
     * LLM提供商配置
     */
    public static class Provider {
        /**
         * 提供商类型
         */
        private String type;

        /**
         * API端点
         */
        private String endpoint;

        /**
         * API密钥
         */
        private String apiKey;

        /**
         * 模型名称
         */
        private String model;

        /**
         * 超时时间
         */
        private Duration timeout = Duration.ofMinutes(5);

        /**
         * 重试次数
         */
        private int retryAttempts = 3;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }
    }

    /**
     * 工具配置
     */
    public static class Tools {
        /**
         * 启用工具
         */
        private boolean enabled = true;

        /**
         * 工具超时时间
         */
        private Duration timeout = Duration.ofMinutes(10);

        /**
         * 最大文件大小（字节）
         */
        private long maxFileSize = 10 * 1024 * 1024; // 10MB

        /**
         * 允许的命令列表
         */
        private String[] allowedCommands = {
            "ls", "cat", "head", "tail", "grep", "find", "echo", "pwd", "date", "whoami", "which",
            "git", "mvn", "npm", "yarn", "docker", "kubectl", "curl", "wget"
        };

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public long getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public String[] getAllowedCommands() {
            return allowedCommands;
        }

        public void setAllowedCommands(String[] allowedCommands) {
            this.allowedCommands = allowedCommands;
        }
    }

    /**
     * Web API配置
     */
    public static class Web {
        /**
         * 启用Web API
         */
        private boolean enabled = true;

        /**
         * API路径前缀
         */
        private String pathPrefix = "/api/pi";

        /**
         * 启用WebSocket
         */
        private boolean websocketEnabled = true;

        /**
         * WebSocket路径
         */
        private String websocketPath = "/ws/pi";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPathPrefix() {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }

        public boolean isWebsocketEnabled() {
            return websocketEnabled;
        }

        public void setWebsocketEnabled(boolean websocketEnabled) {
            this.websocketEnabled = websocketEnabled;
        }

        public String getWebsocketPath() {
            return websocketPath;
        }

        public void setWebsocketPath(String websocketPath) {
            this.websocketPath = websocketPath;
        }
    }
}