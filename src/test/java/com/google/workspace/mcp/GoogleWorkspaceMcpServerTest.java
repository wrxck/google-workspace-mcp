package com.google.workspace.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GoogleWorkspaceMcpServerTest {

    @Nested
    class CollectToolsTests {

        @Test
        void collectsAll28Tools() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            assertEquals(28, tools.size());
        }

        @Test
        void allToolNamesAreUnique() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            Set<String> names = new HashSet<>();
            for (var spec : tools) {
                String name = spec.tool().name();
                assertTrue(names.add(name), "Duplicate tool name: " + name);
            }
        }

        @Test
        void containsDriveTools() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("drive_files_list")));
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("drive_files_delete")));
        }

        @Test
        void containsCalendarTools() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("calendar_events_list")));
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("calendar_events_create")));
        }

        @Test
        void containsSheetsTools() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("sheets_values_get")));
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("sheets_create")));
        }

        @Test
        void containsDocsTools() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("docs_get")));
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("docs_create")));
        }

        @Test
        void containsGmailTools() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("gmail_messages_list")));
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("gmail_messages_send")));
        }

        @Test
        void containsChatTools() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("chat_spaces_list")));
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("chat_messages_create")));
        }

        @Test
        void containsAdminTools() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("admin_users_list")));
            assertTrue(tools.stream().anyMatch(t -> t.tool().name().equals("admin_groups_list")));
        }

        @Test
        void allToolsHaveDescriptions() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            for (var spec : tools) {
                assertNotNull(spec.tool().description(),
                        spec.tool().name() + " missing description");
                assertFalse(spec.tool().description().isBlank(),
                        spec.tool().name() + " has blank description");
            }
        }

        @Test
        void allToolsHaveAnnotations() {
            GwsClient client = new GwsClient("fake-gws", 5);
            List<SyncToolSpecification> tools = GoogleWorkspaceMcpServer.collectTools(client);
            for (var spec : tools) {
                assertNotNull(spec.tool().annotations(),
                        spec.tool().name() + " missing annotations");
            }
        }
    }

    @Nested
    class ParseClaudeBinaryTests {

        @Test
        void returnsNullWhenNoFlag() {
            assertNull(GoogleWorkspaceMcpServer.parseClaudeBinary(new String[]{"--install"}));
        }

        @Test
        void returnsBinaryWhenPresent() {
            assertEquals("/usr/bin/claude",
                    GoogleWorkspaceMcpServer.parseClaudeBinary(
                            new String[]{"--install", "--claude-binary", "/usr/bin/claude"}));
        }

        @Test
        void returnsNullForEmptyArgs() {
            assertNull(GoogleWorkspaceMcpServer.parseClaudeBinary(new String[]{}));
        }

        @Test
        void handlesFlagAtEnd() {
            // --claude-binary at end with no value
            assertNull(GoogleWorkspaceMcpServer.parseClaudeBinary(
                    new String[]{"--install", "--claude-binary"}));
        }
    }

    @Nested
    class ServerConstantsTests {

        @Test
        void serverNameIsGoogleWorkspace() {
            assertEquals("google-workspace", GoogleWorkspaceMcpServer.SERVER_NAME);
        }

        @Test
        void serverVersionIsResolved() {
            // "dev" when running tests without packaged JAR, version string from manifest otherwise
            assertNotNull(GoogleWorkspaceMcpServer.SERVER_VERSION);
            assertFalse(GoogleWorkspaceMcpServer.SERVER_VERSION.isBlank());
        }
    }
}
