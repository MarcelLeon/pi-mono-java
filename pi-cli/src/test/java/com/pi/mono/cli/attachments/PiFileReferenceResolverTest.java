package com.pi.mono.cli.attachments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PiFileReferenceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void expandsRelativeBmpReferencesIntoPngAttachmentBlocks() throws Exception {
        Path bmpFile = tempDir.resolve("sample.bmp");
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.GREEN.getRGB());
        assertTrue(ImageIO.write(image, "bmp", bmpFile.toFile()));

        String resolved = new PiFileReferenceResolver(tempDir)
            .resolve("inspect @sample.bmp please");

        assertTrue(resolved.contains("inspect @sample.bmp please"));
        assertTrue(resolved.contains("<attachment path=\"sample.bmp\""));
        assertTrue(resolved.contains("Content-Type: image/png"));
        assertTrue(resolved.contains("data:image/png;base64,"));
        assertTrue(resolved.contains("</attachment>"));
    }

    @Test
    void recordsMissingFileReferencesWithoutDroppingUserText() {
        String resolved = new PiFileReferenceResolver(tempDir)
            .resolve("inspect @missing.bmp please");

        assertTrue(resolved.contains("inspect @missing.bmp please"));
        assertTrue(resolved.contains("<attachment-error path=\"missing.bmp\""));
        assertTrue(resolved.contains("File not found"));
    }
}
