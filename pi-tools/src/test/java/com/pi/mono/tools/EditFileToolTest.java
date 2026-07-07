package com.pi.mono.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditFileToolTest {

    private final EditFileTool editFileTool = new EditFileTool();

    @TempDir
    Path tempDir;

    @Test
    void replacesOldStringLiterallyInsteadOfAsRegex() throws Exception {
        Path file = tempDir.resolve("note.txt");
        Files.writeString(file, "aXb\na.b\n");

        ToolExecutionResult result = editFileTool.execute(new ToolExecutionRequest(
            "edit",
            Map.of(
                "path", file.toString(),
                "old_string", "a.b",
                "new_string", "literal"
            ),
            "session-id",
            "node-id"
        )).join();

        assertTrue(result.success(), result.content());
        assertEquals("aXb\nliteral\n", Files.readString(file));
    }

    @Test
    void acceptsReplacementStringsWithDollarSignsAndBackslashes() throws Exception {
        Path file = tempDir.resolve("pricing.txt");
        Files.writeString(file, "price = TBD\n");

        ToolExecutionResult result = editFileTool.execute(new ToolExecutionRequest(
            "edit",
            Map.of(
                "path", file.toString(),
                "old_string", "TBD",
                "new_string", "cost $5 \\ path"
            ),
            "session-id",
            "node-id"
        )).join();

        assertTrue(result.success(), result.content());
        assertEquals("price = cost $5 \\ path\n", Files.readString(file));
    }

    @Test
    void ignoresExtraReplacementFieldsWhenRequiredFieldsAreValid() throws Exception {
        Path file = tempDir.resolve("extra.txt");
        Files.writeString(file, "before\n");

        ToolExecutionResult result = editFileTool.execute(new ToolExecutionRequest(
            "edit",
            Map.of(
                "path", file.toString(),
                "old_string", "before",
                "new_string", "after",
                "replacement", Map.of("oldText", "ignored", "newText", "ignored"),
                "edits", "model supplied extra metadata"
            ),
            "session-id",
            "node-id"
        )).join();

        assertTrue(result.success(), result.content());
        assertEquals("after\n", Files.readString(file));
    }
}
