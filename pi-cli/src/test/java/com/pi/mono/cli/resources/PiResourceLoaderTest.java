package com.pi.mono.cli.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiResourceLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsContextFilesFromParentsToCurrentDirectory() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path module = Files.createDirectories(project.resolve("service/api"));
        Files.writeString(project.resolve("AGENTS.md"), "Root agent instructions");
        Files.writeString(module.resolve("CLAUDE.md"), "Module agent instructions");

        PiResources resources = new PiResourceLoader(tempDir.resolve("home")).load(module);

        assertEquals(2, resources.contextFiles().size());
        assertEquals(project.resolve("AGENTS.md"), resources.contextFiles().get(0).path());
        assertEquals(module.resolve("CLAUDE.md"), resources.contextFiles().get(1).path());
        assertTrue(resources.combinedContext().contains("Root agent instructions"));
        assertTrue(resources.combinedContext().contains("Module agent instructions"));
    }

    @Test
    void discoversPromptTemplatesAndSkillsFromPiAndAgentsDirectories() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Files.createDirectories(project.resolve(".pi/prompts"));
        Files.createDirectories(project.resolve(".pi/skills/deploy"));
        Files.createDirectories(project.resolve(".agents/skills/local"));
        Files.writeString(project.resolve(".pi/prompts/review.md"), "Review this code");
        Files.writeString(project.resolve(".pi/skills/deploy/SKILL.md"), "# Deploy Skill");
        Files.writeString(project.resolve(".agents/skills/local/SKILL.md"), "# Local Skill");

        PiResources resources = new PiResourceLoader(tempDir.resolve("home")).load(project);

        assertEquals(1, resources.promptTemplates().size());
        assertEquals("review", resources.promptTemplates().get(0).name());
        assertEquals(project.resolve(".pi/prompts/review.md"), resources.promptTemplates().get(0).path());

        assertEquals(2, resources.skills().size());
        assertTrue(resources.skills().stream().anyMatch(skill -> skill.name().equals("deploy")));
        assertTrue(resources.skills().stream().anyMatch(skill -> skill.name().equals("local")));
    }

    @Test
    void untrustedProjectSkipsProjectLocalPromptsAndSkillsButLoadsGlobalSkills() throws Exception {
        Path userHome = Files.createDirectory(tempDir.resolve("home"));
        Files.createDirectories(userHome.resolve(".agents/skills/global"));
        Files.writeString(userHome.resolve(".agents/skills/global/SKILL.md"), "# Global Skill");

        Path project = Files.createDirectory(tempDir.resolve("project"));
        Files.createDirectories(project.resolve(".pi/prompts"));
        Files.createDirectories(project.resolve(".agents/skills/local"));
        Files.writeString(project.resolve(".pi/prompts/review.md"), "Review this code");
        Files.writeString(project.resolve(".agents/skills/local/SKILL.md"), "# Local Skill");

        PiResources resources = new PiResourceLoader(userHome).load(project, false);

        assertEquals(0, resources.promptTemplates().size());
        assertEquals(1, resources.skills().size());
        assertEquals("global", resources.skills().get(0).name());
    }

    @Test
    void trustedProjectLoadsProjectLocalPromptsAndSkills() throws Exception {
        Path userHome = Files.createDirectory(tempDir.resolve("home"));
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Files.createDirectories(project.resolve(".pi/prompts"));
        Files.createDirectories(project.resolve(".pi/skills/deploy"));
        Files.createDirectories(project.resolve(".agents/skills/local"));
        Files.writeString(project.resolve(".pi/prompts/review.md"), "Review this code");
        Files.writeString(project.resolve(".pi/skills/deploy/SKILL.md"), "# Deploy Skill");
        Files.writeString(project.resolve(".agents/skills/local/SKILL.md"), "# Local Skill");

        PiResources resources = new PiResourceLoader(userHome).load(project, true);

        assertEquals(1, resources.promptTemplates().size());
        assertEquals("review", resources.promptTemplates().get(0).name());
        assertEquals(2, resources.skills().size());
        assertTrue(resources.skills().stream().anyMatch(skill -> skill.name().equals("deploy")));
        assertTrue(resources.skills().stream().anyMatch(skill -> skill.name().equals("local")));
    }

    @Test
    void trustedProjectSettingsCanDisableLocalPromptAndSkillResources() throws Exception {
        Path userHome = Files.createDirectory(tempDir.resolve("home"));
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Files.createDirectories(project.resolve(".pi/prompts"));
        Files.createDirectories(project.resolve(".pi/skills/deploy"));
        Files.writeString(project.resolve(".pi/prompts/review.md"), "Review this code");
        Files.writeString(project.resolve(".pi/skills/deploy/SKILL.md"), "# Deploy Skill");
        Files.writeString(project.resolve(".pi/settings.json"), """
            {
              "prompts": ["-prompts/review.md"],
              "skills": ["-skills/deploy/SKILL.md"]
            }
            """);

        PiResources resources = new PiResourceLoader(userHome).load(project, true);

        assertEquals(0, resources.promptTemplates().size());
        assertEquals(0, resources.skills().size());
    }

    @Test
    void trustedProjectSettingsCanDisableInheritedGlobalSkillResources() throws Exception {
        Path userHome = Files.createDirectory(tempDir.resolve("home"));
        Files.createDirectories(userHome.resolve(".pi/agent/skills/global"));
        Files.writeString(userHome.resolve(".pi/agent/skills/global/SKILL.md"), "# Global Skill");
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Files.createDirectories(project.resolve(".pi"));
        Files.writeString(project.resolve(".pi/settings.json"), """
            {
              "skills": ["-skills/global/SKILL.md"]
            }
            """);

        PiResources resources = new PiResourceLoader(userHome).load(project, true);

        assertEquals(0, resources.skills().size());
    }

    @Test
    void trustedProjectSettingsCanReenableInheritedGlobalSkillResources() throws Exception {
        Path userHome = Files.createDirectory(tempDir.resolve("home"));
        Files.createDirectories(userHome.resolve(".pi/agent/skills/global"));
        Files.writeString(userHome.resolve(".pi/agent/skills/global/SKILL.md"), "# Global Skill");
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Files.createDirectories(project.resolve(".pi"));
        Files.writeString(project.resolve(".pi/settings.json"), """
            {
              "skills": ["-skills/global/SKILL.md", "+skills/global/SKILL.md"]
            }
            """);

        PiResources resources = new PiResourceLoader(userHome).load(project, true);

        assertEquals(1, resources.skills().size());
        assertEquals("global", resources.skills().get(0).name());
    }
}
