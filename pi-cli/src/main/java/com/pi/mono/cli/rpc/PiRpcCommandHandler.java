package com.pi.mono.cli.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.core.SessionNode;
import com.pi.mono.session.SessionManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PiRpcCommandHandler {
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PiRpcCommandHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public String handle(String requestJson) {
        JsonNode id = null;
        try {
            JsonNode request = objectMapper.readTree(requestJson);
            id = request.get("id");
            String method = request.path("method").asText("");
            loadRequestedSession(request.path("params"));

            return switch (method) {
                case "get_entries" -> writeResponse(id, Map.of("result", entriesResult()));
                case "get_tree" -> writeResponse(id, Map.of("result", treeResult()));
                default -> writeResponse(id, Map.of("error", error("unsupported_method", "Unsupported RPC method: " + method)));
            };
        } catch (Exception e) {
            return writeResponse(id, Map.of("error", error("request_failed", e.getMessage())));
        }
    }

    private void loadRequestedSession(JsonNode params) {
        if (params != null && params.hasNonNull("sessionId")) {
            sessionManager.loadSession(params.get("sessionId").asText());
        }
    }

    private Map<String, Object> entriesResult() {
        List<SessionNode> entries = sessionManager.getSessionHistory();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionManager.getCurrentSessionId());
        result.put("currentBranchId", sessionManager.getCurrentBranchId());
        result.put("entries", entries.stream().map(this::nodeEntry).toList());
        return result;
    }

    private Map<String, Object> treeResult() {
        List<SessionNode> nodes = sessionManager.getAllNodes().stream()
            .sorted(Comparator.comparing(SessionNode::timestamp))
            .toList();
        Map<String, List<String>> children = childIndex(nodes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionManager.getCurrentSessionId());
        result.put("rootId", findRootId(nodes));
        result.put("currentBranchId", sessionManager.getCurrentBranchId());
        result.put("nodes", nodes.stream().map(node -> treeNode(node, children)).toList());
        return result;
    }

    private Map<String, Object> nodeEntry(SessionNode node) {
        Map<String, Object> entry = commonNodeFields(node);
        entry.put("messageMetadata", node.message().metadata());
        entry.put("rendered", renderedEntry(node));
        return entry;
    }

    private Map<String, Object> renderedEntry(SessionNode node) {
        String title = switch (node.message().role()) {
            case SYSTEM -> "System";
            case USER -> "User";
            case ASSISTANT -> "Assistant";
            case TOOL_RESULT -> "Tool Result";
        };
        String plainText = node.message().content() == null ? "" : node.message().content();

        Map<String, Object> rendered = new LinkedHashMap<>();
        rendered.put("title", title);
        rendered.put("plainText", plainText);
        rendered.put("markdown", "### " + title + "\n\n" + plainText);
        return rendered;
    }

    private Map<String, Object> treeNode(SessionNode node, Map<String, List<String>> children) {
        Map<String, Object> entry = commonNodeFields(node);
        entry.put("children", children.getOrDefault(node.id(), List.of()));
        return entry;
    }

    private Map<String, Object> commonNodeFields(SessionNode node) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", node.id());
        entry.put("shortId", shortId(node.id()));
        entry.put("parentId", node.parentId());
        entry.put("role", node.message().role().name());
        entry.put("content", node.message().content());
        entry.put("timestamp", node.timestamp().toString());
        entry.put("metadata", node.metadata());
        entry.put("tokenUsage", node.tokenUsage());
        entry.put("version", node.version());
        entry.put("snapshotId", node.snapshotId().orElse(null));
        return entry;
    }

    private String shortId(String id) {
        if (id == null || id.length() <= 8) {
            return id;
        }
        return id.substring(id.length() - 8);
    }

    private Map<String, List<String>> childIndex(List<SessionNode> nodes) {
        Map<String, List<String>> children = new HashMap<>();
        for (SessionNode node : nodes) {
            if (node.parentId() != null) {
                children.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>()).add(node.id());
            }
        }
        return children;
    }

    private String findRootId(List<SessionNode> nodes) {
        return nodes.stream()
            .filter(node -> node.parentId() == null)
            .map(SessionNode::id)
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message == null ? "" : message);
        return error;
    }

    private String writeResponse(JsonNode id, Map<String, Object> fields) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", id);
            response.putAll(fields);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"id\":null,\"error\":{\"code\":\"serialization_failed\",\"message\":\"" + e.getMessage() + "\"}}";
        }
    }
}
