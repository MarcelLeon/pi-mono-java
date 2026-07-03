package com.pi.mono.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.pi.mono.core.SessionNode;
import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.MessageRole;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
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
            validateExistingSessionFileBeforeOverwrite(sessionId, sessionFile);

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
            throw new RuntimeException("Failed to save session: " + sessionId, e);
        }
    }

    private void validateExistingSessionFileBeforeOverwrite(String sessionId, Path sessionFile) throws Exception {
        if (!Files.exists(sessionFile) || Files.size(sessionFile) == 0) {
            return;
        }

        try {
            for (String line : Files.readAllLines(sessionFile)) {
                if (!line.isBlank()) {
                    deserializeSessionNode(line);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                "Refusing to overwrite invalid non-empty session file: " + sessionId,
                e
            );
        }
    }

    public SessionTree loadSessionTree(String sessionId) {
        try {
            Path sessionFile = getSessionFilePath(sessionId);
            if (!Files.exists(sessionFile)) {
                return new SessionTree(); // 返回空的会话树
            }

            List<SessionNode> nodes = new ArrayList<>();
            for (String line : Files.readAllLines(sessionFile)) {
                if (!line.isBlank()) {
                    nodes.add(deserializeSessionNode(line));
                }
            }

            SessionTree restoredTree = new SessionTree();
            restoredTree.restore(nodes, null);
            return restoredTree;

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

    public Path exportSession(String sessionId, Path destination) {
        try {
            Path sessionFile = getSessionFilePath(sessionId);
            if (!Files.exists(sessionFile)) {
                throw new IllegalStateException("Session not found: " + sessionId);
            }
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            return Files.copy(sessionFile, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export session: " + sessionId, e);
        }
    }

    public String importSession(Path source) {
        try {
            if (!Files.exists(source)) {
                throw new IllegalStateException("Import file not found: " + source);
            }

            String sessionId = source.getFileName().toString();
            if (sessionId.endsWith(".jsonl")) {
                sessionId = sessionId.substring(0, sessionId.length() - ".jsonl".length());
            }
            if (sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }

            Path sessionFile = getSessionFilePath(sessionId);
            Files.createDirectories(sessionFile.getParent());
            Files.copy(source, sessionFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            loadSessionTree(sessionId);
            return sessionId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to import session: " + source, e);
        }
    }

    private SessionNode deserializeSessionNode(String jsonLine) throws Exception {
        JsonNode node = objectMapper.readTree(jsonLine);
        JsonNode messageNode = node.get("message");

        AgentMessage message = new AgentMessage(
            MessageRole.valueOf(messageNode.get("role").asText()),
            messageNode.get("content").asText(),
            readMetadata(messageNode.get("metadata"))
        );

        JsonNode snapshotNode = node.get("snapshotId");
        Optional<String> snapshotId = snapshotNode == null || snapshotNode.isNull()
            ? Optional.empty()
            : Optional.of(snapshotNode.asText());

        return new SessionNode(
            node.get("id").asText(),
            readNullableText(node.get("parentId")),
            message,
            LocalDateTime.parse(node.get("timestamp").asText()),
            readMetadata(node.get("metadata")),
            node.get("tokenUsage").asInt(),
            node.get("version").asInt(),
            snapshotId
        );
    }

    private Map<String, Object> readMetadata(JsonNode metadataNode) {
        if (metadataNode == null || metadataNode.isNull() || metadataNode.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> metadata = new HashMap<>();
        metadataNode.fields().forEachRemaining(entry -> metadata.put(entry.getKey(), readJsonValue(entry.getValue())));
        return metadata;
    }

    private Object readJsonValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isObject()) {
            Map<String, Object> nested = new HashMap<>();
            value.fields().forEachRemaining(entry -> nested.put(entry.getKey(), readJsonValue(entry.getValue())));
            return nested;
        }
        if (value.isArray()) {
            List<Object> items = new ArrayList<>();
            value.elements().forEachRemaining(element -> items.add(readJsonValue(element)));
            return items;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isFloatingPointNumber()) {
            return value.asDouble();
        }
        return value.asText();
    }

    private String readNullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
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
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize metadata", e);
        }
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
