package com.pi.mono.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.core.SessionNode;
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
                String jsonLine = objectMapper.writeValueAsString(node);
                Files.write(sessionFile, (jsonLine + "\n").getBytes(),
                          StandardOpenOption.APPEND);
            }

        } catch (Exception e) {
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

    private Path getSessionFilePath(String sessionId) {
        return Paths.get(sessionDir, sessionId + ".jsonl");
    }
}