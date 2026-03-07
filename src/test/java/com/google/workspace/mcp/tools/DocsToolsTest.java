package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocsToolsTest {

    /**
     * Stub GwsClient that captures the args passed to executeJson
     * and returns a canned JSON response.
     */
    static class StubGwsClient extends GwsClient {
        List<String> lastArgs;
        String response = "{}";
        boolean shouldThrow = false;
        String errorMessage = "gws command failed";

        StubGwsClient() {
            super("/bin/true", 5);
        }

        @Override
        public String executeJson(List<String> args) throws IOException {
            this.lastArgs = args;
            if (shouldThrow) {
                throw new IOException(errorMessage);
            }
            return response;
        }
    }

    private StubGwsClient stubClient;
    private DocsTools docsTools;

    @BeforeEach
    void setUp() {
        stubClient = new StubGwsClient();
        docsTools = new DocsTools(stubClient);
    }

    // ── Tool registration ───────────────────────────────────────

    @Test
    void tools_returnsThreeTools() {
        List<SyncToolSpecification> tools = docsTools.tools();
        assertEquals(3, tools.size());
    }

    @Test
    void tools_haveCorrectNames() {
        List<SyncToolSpecification> tools = docsTools.tools();
        List<String> names = tools.stream()
                .map(spec -> spec.tool().name())
                .toList();
        assertTrue(names.contains("docs_get"));
        assertTrue(names.contains("docs_create"));
        assertTrue(names.contains("docs_list"));
    }

    // ── docs_get ────────────────────────────────────────────────

    @Nested
    class DocsGetTests {

        @Test
        void passesDocumentIdCorrectly() {
            stubClient.response = "{\"documentId\":\"abc123\",\"title\":\"Test Doc\"}";

            CallToolResult result = callTool("docs_get", Map.of("documentId", "abc123"));

            assertNotNull(stubClient.lastArgs);
            assertTrue(stubClient.lastArgs.contains("docs"));
            assertTrue(stubClient.lastArgs.contains("documents"));
            assertTrue(stubClient.lastArgs.contains("get"));
            assertTrue(stubClient.lastArgs.contains("--params"));

            // Verify the params JSON contains the documentId
            String paramsJson = stubClient.lastArgs.get(stubClient.lastArgs.indexOf("--params") + 1);
            assertTrue(paramsJson.contains("\"documentId\":\"abc123\""));
        }

        @Test
        void returnsSanitizedResult() {
            stubClient.response = "{\"documentId\":\"abc123\"}";

            CallToolResult result = callTool("docs_get", Map.of("documentId", "abc123"));

            // sanitizedResult produces 2 content items (preamble + wrapped)
            assertEquals(2, result.content().size());
            TextContent preamble = (TextContent) result.content().get(0);
            assertTrue(preamble.text().contains("SECURITY CONTEXT"));
            TextContent wrapped = (TextContent) result.content().get(1);
            assertTrue(wrapped.text().contains("{\"documentId\":\"abc123\"}"));
        }

        @Test
        void missingDocumentId_returnsError() {
            CallToolResult result = callTool("docs_get", Map.of());

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("documentId"));
        }

        @Test
        void blankDocumentId_returnsError() {
            CallToolResult result = callTool("docs_get", Map.of("documentId", "  "));

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("documentId"));
        }
    }

    // ── docs_create ─────────────────────────────────────────────

    @Nested
    class DocsCreateTests {

        @Test
        void passesTitleInJsonBody() {
            stubClient.response = "{\"documentId\":\"new123\",\"title\":\"Meeting Notes\"}";

            CallToolResult result = callTool("docs_create", Map.of("title", "Meeting Notes"));

            assertNotNull(stubClient.lastArgs);
            assertTrue(stubClient.lastArgs.contains("docs"));
            assertTrue(stubClient.lastArgs.contains("documents"));
            assertTrue(stubClient.lastArgs.contains("create"));
            assertTrue(stubClient.lastArgs.contains("--json"));

            // Verify the JSON body contains the title
            String bodyJson = stubClient.lastArgs.get(stubClient.lastArgs.indexOf("--json") + 1);
            assertTrue(bodyJson.contains("\"title\":\"Meeting Notes\""));
        }

        @Test
        void returnsPlainResult() {
            stubClient.response = "{\"documentId\":\"new123\"}";

            CallToolResult result = callTool("docs_create", Map.of("title", "Test"));

            // plainResult produces 1 content item
            assertEquals(1, result.content().size());
            TextContent content = (TextContent) result.content().get(0);
            assertEquals("{\"documentId\":\"new123\"}", content.text());
        }

        @Test
        void missingTitle_returnsError() {
            CallToolResult result = callTool("docs_create", Map.of());

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("title"));
        }
    }

    // ── docs_list ───────────────────────────────────────────────

    @Nested
    class DocsListTests {

        @Test
        void buildsDriveQueryWithMimeTypeFilter() {
            stubClient.response = "{\"files\":[]}";

            CallToolResult result = callTool("docs_list", Map.of());

            assertNotNull(stubClient.lastArgs);
            assertTrue(stubClient.lastArgs.contains("drive"));
            assertTrue(stubClient.lastArgs.contains("files"));
            assertTrue(stubClient.lastArgs.contains("list"));
            assertTrue(stubClient.lastArgs.contains("--params"));

            String paramsJson = stubClient.lastArgs.get(stubClient.lastArgs.indexOf("--params") + 1);
            assertTrue(paramsJson.contains("application/vnd.google-apps.document"));
            assertTrue(paramsJson.contains("mimeType"));
        }

        @Test
        void usesDefaultPageSize() {
            stubClient.response = "{\"files\":[]}";

            callTool("docs_list", Map.of());

            String paramsJson = stubClient.lastArgs.get(stubClient.lastArgs.indexOf("--params") + 1);
            assertTrue(paramsJson.contains("\"pageSize\":20"));
        }

        @Test
        void usesCustomPageSize() {
            stubClient.response = "{\"files\":[]}";

            callTool("docs_list", Map.of("pageSize", 50));

            String paramsJson = stubClient.lastArgs.get(stubClient.lastArgs.indexOf("--params") + 1);
            assertTrue(paramsJson.contains("\"pageSize\":50"));
        }

        @Test
        void returnsSanitizedResult() {
            stubClient.response = "{\"files\":[{\"id\":\"1\"}]}";

            CallToolResult result = callTool("docs_list", Map.of());

            assertEquals(2, result.content().size());
            TextContent preamble = (TextContent) result.content().get(0);
            assertTrue(preamble.text().contains("SECURITY CONTEXT"));
        }
    }

    // ── Error handling ──────────────────────────────────────────

    @Nested
    class ErrorHandlingTests {

        @Test
        void gwsClientError_returnsErrorResult() {
            stubClient.shouldThrow = true;
            stubClient.errorMessage = "gws command failed (exit 1): Not found";

            CallToolResult result = callTool("docs_get", Map.of("documentId", "bad-id"));

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("Not found"));
        }

        @Test
        void gwsClientError_onCreate_returnsErrorResult() {
            stubClient.shouldThrow = true;
            stubClient.errorMessage = "Authentication required";

            CallToolResult result = callTool("docs_create", Map.of("title", "Test"));

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("Authentication required"));
        }

        @Test
        void gwsClientError_onList_returnsErrorResult() {
            stubClient.shouldThrow = true;
            stubClient.errorMessage = "Rate limit exceeded";

            CallToolResult result = callTool("docs_list", Map.of());

            assertTrue(result.isError());
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("Rate limit exceeded"));
        }
    }

    // ── Type validation ─────────────────────────────────────────

    @Nested
    class TypeValidation {

        @Test
        void requireStringRejectsInteger() {
            CallToolResult result = callTool("docs_get", Map.of("documentId", 12345));
            assertTrue(result.isError(), "Integer should be rejected as documentId");
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("documentId"));
        }

        @Test
        void requireStringRejectsBoolean() {
            CallToolResult result = callTool("docs_create", Map.of("title", false));
            assertTrue(result.isError(), "Boolean should be rejected as title");
            TextContent content = (TextContent) result.content().get(0);
            assertTrue(content.text().contains("title"));
        }
    }

    // ── Helper ──────────────────────────────────────────────────

    private CallToolResult callTool(String toolName, Map<String, Object> args) {
        List<SyncToolSpecification> tools = docsTools.tools();
        SyncToolSpecification spec = tools.stream()
                .filter(t -> t.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
        // Build a CallToolRequest with the given arguments
        CallToolRequest request = new CallToolRequest(toolName, new HashMap<>(args));
        // Pass null exchange since our handlers don't use it
        return spec.callHandler().apply(null, request);
    }
}
