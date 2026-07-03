package com.pi.mono.cli.resources;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiResourceCommandResolverTest {

    @Test
    void expandsPromptTemplateSlashCommandWithKeyValueArguments() {
        PiResources resources = new PiResources(
            List.of(),
            List.of(new PiResources.ResourceFile("review", Path.of("review.md"), "Review {{target}} for {{focus}}")),
            List.of(),
            ""
        );

        String resolved = new PiResourceCommandResolver(resources)
            .resolve("/review target=api focus=security")
            .orElseThrow();

        assertEquals("Review api for security", resolved);
    }

    @Test
    void resolvesSkillInvocationWithOptionalRequest() {
        PiResources resources = new PiResources(
            List.of(),
            List.of(),
            List.of(new PiResources.ResourceFile("deploy", Path.of("SKILL.md"), "# Deploy Skill\nUse deployment steps.")),
            ""
        );

        String resolved = new PiResourceCommandResolver(resources)
            .resolve("/skill:deploy release service")
            .orElseThrow();

        assertTrue(resolved.contains("# Deploy Skill"));
        assertTrue(resolved.contains("User request: release service"));
    }
}
