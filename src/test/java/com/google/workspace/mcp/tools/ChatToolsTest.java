package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatToolsTest {

    // ── StubGwsClient ─────────────────────────────────────────────

    /**
     * Captures the args passed to executeJson and returns a canned response.
     */
    static class StubGwsClient extends GwsClient {
        List<String> lastArgs;
        String cannedResponse = "{\"ok\":true}";
        IOException failWith;

        StubGwsClient() {
            super("/bin/false", 1);
        }

        @Override
        public String executeJson(List<String> args) throws IOException {
            lastArgs = args;
            if (failWith != null) {
                throw failWith;
            }
            return cannedResponse;
        }
    }

    // ── Helper ────────────────────────────────────────────────────

    private CallToolResult call(SyncToolSpecification spec, Map<String, Object> args) {
        CallToolRequest request = new CallToolRequest(spec.tool().name(), args);
        return spec.callHandler().apply(null, request);
    }

    private SyncToolSpecification findTool(ChatTools tools, String name) {
        return tools.tools().stream()
                .filter(t -> t.tool().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + name));
    }

    private String textContent(CallToolResult result, int index) {
        return ((TextContent) result.content().get(index)).text();
    }

    // ── tools() ───────────────────────────────────────────────────

    @Nested
    class ToolRegistration {

        @Test
        void returns3Tools() {
            ChatTools chat = new ChatTools(new StubGwsClient());
            assertEquals(3, chat.tools().size());
        }

        @Test
        void toolNamesAreCorrect() {
            ChatTools chat = new ChatTools(new StubGwsClient());
            List<String> names = chat.tools().stream()
                    .map(t -> t.tool().name())
                    .toList();
            assertTrue(names.contains("chat_spaces_list"));
            assertTrue(names.contains("chat_messages_list"));
            assertTrue(names.contains("chat_messages_create"));
        }
    }

    // ── chat_spaces_list ──────────────────────────────────────────

    @Nested
    class SpacesListTests {

        @Test
        void invokesCorrectGwsCommand() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_spaces_list");

            call(spec, null);

            assertEquals(List.of("chat", "spaces", "list"), stub.lastArgs);
        }

        @Test
        void returnsSanitizedResult() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"spaces\":[]}";
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_spaces_list");

            CallToolResult result = call(spec, null);

            assertTrue(result.isError() == null || !result.isError());
            assertEquals(2, result.content().size(), "sanitizedResult produces 2 content items");
            assertTrue(textContent(result, 0).contains("SECURITY CONTEXT"));
            assertTrue(textContent(result, 1).contains("{\"spaces\":[]}"));
        }

        @Test
        void returnsErrorOnFailure() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("connection refused");
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_spaces_list");

            CallToolResult result = call(spec, null);

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("chat_spaces_list failed"));
            assertTrue(textContent(result, 0).contains("connection refused"));
        }
    }

    // ── chat_messages_list ────────────────────────────────────────

    @Nested
    class MessagesListTests {

        @Test
        void addsSpacesPrefixWhenMissing() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_list");

            call(spec, Map.of("spaceId", "AAAA1234"));

            assertTrue(stub.lastArgs.contains("--params"));
            String paramsArg = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(paramsArg.contains("\"parent\":\"spaces/AAAA1234\""),
                    "Should add spaces/ prefix, got: " + paramsArg);
        }

        @Test
        void doesNotDoubleAddSpacesPrefix() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_list");

            call(spec, Map.of("spaceId", "spaces/AAAA1234"));

            String paramsArg = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(paramsArg.contains("\"parent\":\"spaces/AAAA1234\""),
                    "Should not double-add prefix, got: " + paramsArg);
            assertFalse(paramsArg.contains("spaces/spaces/"),
                    "Should not have double spaces/ prefix, got: " + paramsArg);
        }

        @Test
        void invokesCorrectGwsCommand() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_list");

            call(spec, Map.of("spaceId", "ABC"));

            assertEquals("chat", stub.lastArgs.get(0));
            assertEquals("spaces", stub.lastArgs.get(1));
            assertEquals("messages", stub.lastArgs.get(2));
            assertEquals("list", stub.lastArgs.get(3));
            assertEquals("--params", stub.lastArgs.get(4));
        }

        @Test
        void returnsSanitizedResult() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"messages\":[]}";
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_list");

            CallToolResult result = call(spec, Map.of("spaceId", "X"));

            assertTrue(result.isError() == null || !result.isError());
            assertEquals(2, result.content().size());
        }

        @Test
        void failsWhenSpaceIdMissing() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_list");

            CallToolResult result = call(spec, Map.of());

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'spaceId' is required"));
        }

        @Test
        void failsWhenSpaceIdIsBlank() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_list");

            CallToolResult result = call(spec, Map.of("spaceId", "   "));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'spaceId' is required"));
        }

        @Test
        void returnsErrorOnFailure() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("timeout");
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_list");

            CallToolResult result = call(spec, Map.of("spaceId", "X"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("chat_messages_list failed"));
        }
    }

    // ── chat_messages_create ──────────────────────────────────────

    @Nested
    class MessagesCreateTests {

        @Test
        void invokesCorrectGwsCommandWithParentAndText() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_create");

            call(spec, Map.of("spaceId", "AAAA1234", "text", "Hello world"));

            assertEquals("chat", stub.lastArgs.get(0));
            assertEquals("spaces", stub.lastArgs.get(1));
            assertEquals("messages", stub.lastArgs.get(2));
            assertEquals("create", stub.lastArgs.get(3));

            String paramsArg = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(paramsArg.contains("\"parent\":\"spaces/AAAA1234\""));

            String jsonArg = stub.lastArgs.get(stub.lastArgs.indexOf("--json") + 1);
            assertTrue(jsonArg.contains("\"text\":\"Hello world\""));
        }

        @Test
        void addsSpacesPrefixWhenMissing() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_create");

            call(spec, Map.of("spaceId", "XYZ", "text", "hi"));

            String paramsArg = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(paramsArg.contains("\"parent\":\"spaces/XYZ\""));
        }

        @Test
        void doesNotDoubleAddSpacesPrefix() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_create");

            call(spec, Map.of("spaceId", "spaces/XYZ", "text", "hi"));

            String paramsArg = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(paramsArg.contains("\"parent\":\"spaces/XYZ\""));
            assertFalse(paramsArg.contains("spaces/spaces/"));
        }

        @Test
        void returnsPlainResult() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"name\":\"spaces/X/messages/1\"}";
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_create");

            CallToolResult result = call(spec, Map.of("spaceId", "X", "text", "hi"));

            assertTrue(result.isError() == null || !result.isError());
            assertEquals(1, result.content().size(), "plainResult produces 1 content item");
            assertEquals("{\"name\":\"spaces/X/messages/1\"}", textContent(result, 0));
        }

        @Test
        void failsWhenSpaceIdMissing() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_create");

            CallToolResult result = call(spec, Map.of("text", "hi"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'spaceId' is required"));
        }

        @Test
        void failsWhenTextMissing() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_create");

            CallToolResult result = call(spec, Map.of("spaceId", "X"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'text' is required"));
        }

        @Test
        void failsWhenBothArgsMissing() {
            StubGwsClient stub = new StubGwsClient();
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_create");

            CallToolResult result = call(spec, null);

            assertTrue(result.isError());
        }

        @Test
        void returnsErrorOnFailure() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("forbidden");
            ChatTools chat = new ChatTools(stub);
            SyncToolSpecification spec = findTool(chat, "chat_messages_create");

            CallToolResult result = call(spec, Map.of("spaceId", "X", "text", "hi"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("chat_messages_create failed"));
            assertTrue(textContent(result, 0).contains("forbidden"));
        }
    }

    // ── ensureSpacesPrefix ────────────────────────────────────────

    @Nested
    class EnsureSpacesPrefixTests {

        @Test
        void addsPrefixWhenMissing() {
            assertEquals("spaces/ABC", ChatTools.ensureSpacesPrefix("ABC"));
        }

        @Test
        void doesNotDoublePrefix() {
            assertEquals("spaces/ABC", ChatTools.ensureSpacesPrefix("spaces/ABC"));
        }

        @Test
        void handlesUppercaseSpacesPrefix() {
            // "SPACES/" is not the same as "spaces/" — should still add prefix
            String result = ChatTools.ensureSpacesPrefix("SPACES/ABC");
            assertEquals("spaces/SPACES/ABC", result,
                    "Uppercase SPACES/ should not be treated as valid prefix");
        }

        @Test
        void handlesEmptyStringGracefully() {
            String result = ChatTools.ensureSpacesPrefix("");
            assertEquals("spaces/", result);
        }
    }
}
