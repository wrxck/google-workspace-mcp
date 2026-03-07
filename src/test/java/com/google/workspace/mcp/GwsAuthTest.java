package com.google.workspace.mcp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GwsAuthTest {

    // ── resolveJarPath ────────────────────────────────────────────

    @Nested
    class ResolveJarPathTests {

        @Test
        void returnsNonNull() {
            String path = GwsAuth.resolveJarPath();
            assertNotNull(path);
        }

        @Test
        void returnsAbsolutePath() {
            String path = GwsAuth.resolveJarPath();
            assertTrue(path.startsWith("/"), "Expected absolute path, got: " + path);
        }

        @Test
        void fallbackPathContainsArtifactName() {
            // When not running from a JAR (i.e. during tests), the fallback is used
            String path = GwsAuth.resolveJarPath();
            assertTrue(path.contains("google-workspace-mcp"),
                    "Expected fallback path containing 'google-workspace-mcp', got: " + path);
        }
    }

    // ── getMcpServerName ──────────────────────────────────────────

    @Nested
    class GetMcpServerNameTests {

        @Test
        void returnsExpectedName() {
            assertEquals("google-workspace", GwsAuth.getMcpServerName());
        }
    }

    // ── findGwsBinary ─────────────────────────────────────────────

    @Nested
    class FindGwsBinaryTests {

        @Test
        void isCallableAndHandlesGracefully() {
            // May return null or a path depending on the environment;
            // the key assertion is that it does not throw.
            String result = GwsAuth.findGwsBinary();
            // result is either null (not installed) or a non-empty string
            if (result != null) {
                assertFalse(result.isBlank());
            }
        }
    }

    // ── findClaudeBinary ──────────────────────────────────────────

    @Nested
    class FindClaudeBinaryTests {

        @Test
        void isCallableAndHandlesGracefully() {
            // May return null or a path depending on the environment;
            // the key assertion is that it does not throw.
            String result = GwsAuth.findClaudeBinary();
            if (result != null) {
                assertFalse(result.isBlank());
            }
        }

        @Test
        void candidatesIncludeClaudeLocalPath() {
            String[] candidates = GwsAuth.getClaudeBinaryCandidates();
            List<String> list = Arrays.asList(candidates);
            String home = System.getProperty("user.home");
            assertTrue(list.contains(home + "/.claude/local/claude"),
                    "Expected ~/.claude/local/claude in candidates");
        }

        @Test
        void candidatesIncludeNpxFallback() {
            String[] candidates = GwsAuth.getClaudeBinaryCandidates();
            List<String> list = Arrays.asList(candidates);
            assertTrue(list.contains("npx @anthropic-ai/claude-code"),
                    "Expected npx fallback in candidates");
        }

        @Test
        void candidatesStartWithBareCommand() {
            String[] candidates = GwsAuth.getClaudeBinaryCandidates();
            assertEquals("claude", candidates[0],
                    "First candidate should be bare 'claude' for PATH lookup");
        }
    }

    // ── getClaudeBinaryCandidates ──────────────────────────────────

    @Nested
    class GetClaudeBinaryCandidatesTests {

        @Test
        void returnsNonEmptyArray() {
            String[] candidates = GwsAuth.getClaudeBinaryCandidates();
            assertTrue(candidates.length > 0, "Should have at least one candidate");
        }

        @Test
        void allCandidatesAreNonBlank() {
            for (String candidate : GwsAuth.getClaudeBinaryCandidates()) {
                assertFalse(candidate.isBlank(), "No candidate should be blank");
            }
        }

        @Test
        void npxCandidateIsSplittable() {
            // Verify the npx candidate can be split into valid ProcessBuilder args
            String npxCandidate = null;
            for (String c : GwsAuth.getClaudeBinaryCandidates()) {
                if (c.contains("npx")) {
                    npxCandidate = c;
                    break;
                }
            }
            assertNotNull(npxCandidate, "Should have an npx candidate");
            String[] parts = npxCandidate.split(" ");
            assertEquals(2, parts.length, "npx candidate should split into exactly 2 parts");
            assertEquals("npx", parts[0]);
            assertTrue(parts[1].contains("claude"), "Second part should reference claude");
        }

        @Test
        void candidatesEndWithNpxFallback() {
            String[] candidates = GwsAuth.getClaudeBinaryCandidates();
            String last = candidates[candidates.length - 1];
            assertTrue(last.contains("npx"), "Last candidate should be npx fallback");
        }
    }

    // ── installSkill ───────────────────────────────────────────────

    @Nested
    class InstallSkillTests {

        @Test
        void skillResourceExistsInClasspath() {
            var resource = GwsAuth.class.getResourceAsStream("/skill/SKILL.md");
            assertNotNull(resource, "skill/SKILL.md should be available as classpath resource");
        }

        @Test
        void skillResourceContainsExpectedContent() throws Exception {
            try (var in = GwsAuth.class.getResourceAsStream("/skill/SKILL.md")) {
                assertNotNull(in);
                String content = new String(in.readAllBytes());
                assertTrue(content.contains("Google Workspace MCP Tools"),
                        "Skill should contain header");
                assertTrue(content.contains("drive_files_list"),
                        "Skill should reference Drive tools");
                assertTrue(content.contains("gmail_messages_send"),
                        "Skill should reference Gmail tools");
            }
        }

        @Test
        void skillResourceStartsWithFrontmatter() throws Exception {
            try (var in = GwsAuth.class.getResourceAsStream("/skill/SKILL.md")) {
                assertNotNull(in);
                String content = new String(in.readAllBytes());
                assertTrue(content.startsWith("---"),
                        "Skill should start with YAML frontmatter");
                assertTrue(content.contains("name: gws"),
                        "Skill should have name: gws in frontmatter");
            }
        }

        @Test
        void skillResourceCoversAllToolGroups() throws Exception {
            try (var in = GwsAuth.class.getResourceAsStream("/skill/SKILL.md")) {
                assertNotNull(in);
                String content = new String(in.readAllBytes());
                assertTrue(content.contains("### Drive"), "Should cover Drive tools");
                assertTrue(content.contains("### Calendar"), "Should cover Calendar tools");
                assertTrue(content.contains("### Sheets"), "Should cover Sheets tools");
                assertTrue(content.contains("### Docs"), "Should cover Docs tools");
                assertTrue(content.contains("### Gmail"), "Should cover Gmail tools");
                assertTrue(content.contains("### Chat"), "Should cover Chat tools");
                assertTrue(content.contains("### Admin"), "Should cover Admin tools");
            }
        }
    }

    // ── isAlreadyRegistered ───────────────────────────────────────

    @Nested
    class IsAlreadyRegisteredTests {

        @Test
        void returnsFalseForNonexistentBinary() {
            boolean registered = GwsAuth.isAlreadyRegistered("/nonexistent/binary/claude-fake-12345");
            assertFalse(registered);
        }

        @Test
        void returnsFalseForNullBinary() {
            // Passing null should not throw; it should return false gracefully
            boolean registered = GwsAuth.isAlreadyRegistered(null);
            assertFalse(registered);
        }
    }
}
