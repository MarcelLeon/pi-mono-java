package com.pi.mono.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.core.SessionNode;
import com.pi.mono.core.AgentMessage;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Stream;

/**
 * 会话持久化存储
 */
@Component
public class SessionPersistence {

    @Value("${pi.session.dir:.pi/sessions}")
    private String sessionDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 设置会话目录（用于测试）
     */
    public void setSessionDir(String sessionDir) {
        this.sessionDir = sessionDir;
    }

    public void saveSessionTree(String sessionId, SessionTree sessionTree) {
        try {
            Path sessionFile = getSessionFilePath(sessionId);
            Files.createDirectories(sessionFile.getParent());

            // 转换为可序列化的版本
            SerializableSessionTree serializableTree = new SerializableSessionTree(sessionTree);

            // 清空文件并重新写入
            Files.deleteIfExists(sessionFile);
            Files.createFile(sessionFile);

            List<SessionNode> allNodes = new ArrayList<>(serializableTree.getAllNodes());

            // 按时间排序
            allNodes.sort(Comparator.comparing(SessionNode::timestamp));

            for (SessionNode node : allNodes) {
                // 手动序列化，避免Optional问题
                String jsonLine = serializeSessionNode(node);
                Files.write(sessionFile, (jsonLine + "\n").getBytes(),
                          StandardOpenOption.APPEND);
            }

        } catch (Exception e) {
            System.err.println("保存会话失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to save session: " + sessionId, e);
        }
    }

    public SessionTree loadSessionTree(String sessionId) {
        try {
            Path sessionFile = getSessionFilePath(sessionId);
            if (!Files.exists(sessionFile)) {
                return new SessionTree(); // 返回空的会话树
            }

            // 从文件重建SessionTree
            // 由于SessionTree是@Component，我们创建一个新的实例
            // 这里需要更复杂的逻辑来重建树结构
            // 暂时返回空树，后续优化
            return new SessionTree();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load session: " + sessionId, e);
        }
    }

    public List<String> listSessions() {
        try {
            Path dir = Paths.get(sessionDir);
            if (!Files.exists(dir)) {
                return Collections.emptyList();
            }

            try (Stream<Path> files = Files.list(dir)) {
                return files
                    .filter(path -> path.toString().endsWith(".jsonl"))
                    .map(path -> path.getFileName().toString().replace(".jsonl", ""))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to list sessions", e);
        }
    }

    public boolean sessionExists(String sessionId) {
        return Files.exists(getSessionFilePath(sessionId));
    }

    /**
     * 获取会话目录（用于测试）
     */
    protected String getSessionDir() {
        return sessionDir;
    }

    private Path getSessionFilePath(String sessionId) {
        return Paths.get(sessionDir, sessionId + ".jsonl");
    }

    /**
     * 手动序列化SessionNode为JSON字符串，避免Optional类型序列化问题
     */
    private String serializeSessionNode(SessionNode node) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        // 添加必需字段
        json.append("\"id\":").append(escapeJsonString(node.id())).append(",");
        json.append("\"parentId\":").append(node.parentId() != null ? escapeJsonString(node.parentId()) : "null").append(",");
        json.append("\"message\":").append(serializeAgentMessage(node.message())).append(",");
        json.append("\"timestamp\":\"").append(node.timestamp().toString()).append("\",");
        json.append("\"metadata\":").append(serializeMetadata(node.metadata())).append(",");
        json.append("\"tokenUsage\":").append(node.tokenUsage()).append(",");
        json.append("\"version\":").append(node.version());

        // 处理snapshotId字段
        if (node.snapshotId().isPresent()) {
            json.append(",\"snapshotId\":").append(escapeJsonString(node.snapshotId().get()));
        } else {
            json.append(",\"snapshotId\":null");
        }

        json.append("}");
        return json.toString();
    }

    /**
     * 序列化AgentMessage为JSON
     */
    private String serializeAgentMessage(AgentMessage message) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        // 添加必需字段
        json.append("\"role\":\"").append(message.role().name()).append("\",");
        json.append("\"content\":").append(escapeJsonString(message.content())).append(",");
        json.append("\"metadata\":").append(serializeMetadata(message.metadata()));

        json.append("}");
        return json.toString();
    }

    /**
     * 序列化metadata Map为JSON
     */
    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder();
        json.append("{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(escapeJsonString(entry.getKey())).append("\":");

            // 处理不同的对象类型
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append(escapeJsonString((String) value));
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else {
                // 其他类型转换为字符串
                json.append(escapeJsonString(String.valueOf(value)));
            }
            first = false;
        }

        json.append("}");
        return json.toString();
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "null";
        }

        // 使用简单的转义，避免依赖外部库
        return "\"" + str.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t") + "\"";
    }
}