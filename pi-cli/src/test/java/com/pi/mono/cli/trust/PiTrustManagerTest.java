package com.pi.mono.cli.trust;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiTrustManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void unknownProjectIsNotTrusted() {
        PiTrustManager manager = new PiTrustManager(tempDir.resolve("trust-java.txt"));

        assertFalse(manager.isTrusted(tempDir.resolve("project")));
    }

    @Test
    void trustingAPathTrustsChildren() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project/service"));
        PiTrustManager manager = new PiTrustManager(tempDir.resolve("trust-java.txt"));

        manager.trust(tempDir.resolve("project"));

        assertTrue(manager.isTrusted(project));
    }

    @Test
    void trustDecisionPersistsAcrossInstances() {
        Path trustFile = tempDir.resolve("trust-java.txt");
        Path project = tempDir.resolve("project");

        new PiTrustManager(trustFile).trust(project);

        assertTrue(new PiTrustManager(trustFile).isTrusted(project));
    }
}
