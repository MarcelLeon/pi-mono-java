package com.pi.mono.cli.attachments;

import com.pi.mono.tools.ReadFileTool;
import com.pi.mono.tools.ToolExecutionRequest;
import com.pi.mono.tools.ToolExecutionResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PiFileReferenceResolver {

    private static final Pattern FILE_REFERENCE = Pattern.compile("(^|\\s)@([^\\s]+)");

    private final Path workingDirectory;
    private final ReadFileTool readFileTool;

    public PiFileReferenceResolver(Path workingDirectory) {
        this(workingDirectory, new ReadFileTool());
    }

    PiFileReferenceResolver(Path workingDirectory, ReadFileTool readFileTool) {
        this.workingDirectory = workingDirectory == null
            ? Path.of("").toAbsolutePath().normalize()
            : workingDirectory.toAbsolutePath().normalize();
        this.readFileTool = readFileTool;
    }

    public String resolve(String input) {
        if (input == null || input.isBlank() || !input.contains("@")) {
            return input;
        }

        Matcher matcher = FILE_REFERENCE.matcher(input);
        StringBuilder attachments = new StringBuilder();
        while (matcher.find()) {
            String pathText = trimTrailingPunctuation(matcher.group(2));
            if (!pathText.isBlank()) {
                attachments.append(renderAttachment(pathText));
            }
        }

        if (attachments.isEmpty()) {
            return input;
        }

        return input + "\n\n[Attached files]\n" + attachments;
    }

    private String renderAttachment(String pathText) {
        Path resolvedPath = resolvePath(pathText);
        ToolExecutionResult result = readFileTool.execute(new ToolExecutionRequest(
            "read",
            Map.of("path", resolvedPath.toString()),
            "cli-file-reference",
            null
        )).join();

        if (!result.success()) {
            return "<attachment-error path=\"" + escapeAttribute(pathText) + "\" resolvedPath=\""
                + escapeAttribute(resolvedPath.toString()) + "\">\n"
                + result.content() + "\n"
                + "</attachment-error>\n";
        }

        return "<attachment path=\"" + escapeAttribute(pathText) + "\" resolvedPath=\""
            + escapeAttribute(resolvedPath.toString()) + "\">\n"
            + result.content()
            + "</attachment>\n";
    }

    private Path resolvePath(String pathText) {
        Path path = Path.of(pathText);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return workingDirectory.resolve(path).normalize();
    }

    private String trimTrailingPunctuation(String pathText) {
        String trimmed = pathText;
        while (trimmed.endsWith(",") || trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String escapeAttribute(String value) {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
