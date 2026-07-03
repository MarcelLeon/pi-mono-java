package com.pi.mono.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadFileToolTest {

    private final ReadFileTool readFileTool = new ReadFileTool();

    @TempDir
    Path tempDir;

    @Test
    void convertsBmpFilesToPngDataUrls() throws Exception {
        Path bmpFile = tempDir.resolve("sample.bmp");
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.BLUE.getRGB());
        assertTrue(ImageIO.write(image, "bmp", bmpFile.toFile()));

        ToolExecutionResult result = readFileTool.execute(new ToolExecutionRequest(
            "read",
            Map.of("path", bmpFile.toString()),
            "session-id",
            "node-id"
        )).join();

        assertTrue(result.success());
        assertTrue(result.content().contains("Content-Type: image/png"));
        assertTrue(result.content().contains("data:image/png;base64,"));
        assertEquals("image", result.metadata().get("contentType"));
        assertEquals("image/png", result.metadata().get("mimeType"));
        assertEquals("bmp", result.metadata().get("sourceFormat"));
        assertEquals("png", result.metadata().get("convertedFormat"));
    }
}
