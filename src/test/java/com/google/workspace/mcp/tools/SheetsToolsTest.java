package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SheetsToolsTest {

    // ── StubGwsClient ───────────────────────────────────────────────

    /**
     * A GwsClient subclass that captures the command arguments passed to
     * executeJson and returns a canned response, avoiding real subprocess execution.
     */
    static class StubGwsClient extends GwsClient {

        private List<String> lastArgs;
        private String cannedResponse = "{}";
        private IOException failWith;

        StubGwsClient() {
            super("/bin/true", 5);
        }

        @Override
        public String executeJson(List<String> args) throws IOException {
            this.lastArgs = List.copyOf(args);
            if (failWith != null) throw failWith;
            return cannedResponse;
        }

        List<String> lastArgs() {
            return lastArgs;
        }

        void setCannedResponse(String json) {
            this.cannedResponse = json;
        }

        void setFailWith(IOException ex) {
            this.failWith = ex;
        }
    }

    // ── Fields ──────────────────────────────────────────────────────

    private StubGwsClient stub;
    private SheetsTools sheetsTools;
    private List<SyncToolSpecification> specs;

    @BeforeEach
    void setUp() {
        stub = new StubGwsClient();
        sheetsTools = new SheetsTools(stub);
        specs = sheetsTools.tools();
    }

    // ── Tool registration ───────────────────────────────────────────

    @Nested
    class ToolRegistration {

        @Test
        void returns4Tools() {
            assertEquals(4, specs.size());
        }

        @Test
        void toolNamesAreCorrect() {
            Set<String> names = specs.stream()
                    .map(s -> s.tool().name())
                    .collect(Collectors.toSet());
            assertEquals(Set.of("sheets_list", "sheets_values_get",
                    "sheets_values_update", "sheets_create"), names);
        }
    }

    // ── sheets_list ─────────────────────────────────────────────────

    @Nested
    class SheetsList {

        @Test
        void buildsDriveQueryWithMimeTypeFilter() {
            CallToolResult result = callTool("sheets_list", Map.of());
            assertNotNull(result);
            assertFalse(isError(result));

            List<String> args = stub.lastArgs();
            assertEquals("drive", args.get(0));
            assertEquals("files", args.get(1));
            assertEquals("list", args.get(2));
            assertEquals("--params", args.get(3));

            String params = args.get(4);
            assertTrue(params.contains("mimeType='application/vnd.google-apps.spreadsheet'"),
                    "Should contain spreadsheet mimeType filter, got: " + params);
        }

        @Test
        void defaultPageSizeIs20() {
            callTool("sheets_list", Map.of());
            String params = stub.lastArgs().get(4);
            assertTrue(params.contains("\"pageSize\":20"),
                    "Default pageSize should be 20, got: " + params);
        }

        @Test
        void respectsCustomPageSize() {
            callTool("sheets_list", Map.of("pageSize", 5));
            String params = stub.lastArgs().get(4);
            assertTrue(params.contains("\"pageSize\":5"),
                    "Should use custom pageSize, got: " + params);
        }

        @Test
        void returnsSanitizedResult() {
            stub.setCannedResponse("{\"files\":[]}");
            CallToolResult result = callTool("sheets_list", Map.of());
            // sanitizedResult produces 2 content items: preamble + wrapped
            assertEquals(2, result.content().size(),
                    "Read-only tool should return sanitizedResult (2 content items)");
        }
    }

    // ── sheets_values_get ───────────────────────────────────────────

    @Nested
    class SheetsValuesGet {

        @Test
        void passesSpreadsheetIdAndRange() {
            callTool("sheets_values_get", Map.of(
                    "spreadsheetId", "abc123",
                    "range", "Sheet1!A1:C10"));

            List<String> args = stub.lastArgs();
            assertEquals("sheets", args.get(0));
            assertEquals("spreadsheets", args.get(1));
            assertEquals("values", args.get(2));
            assertEquals("get", args.get(3));
            assertEquals("--params", args.get(4));

            String params = args.get(5);
            assertTrue(params.contains("\"spreadsheetId\":\"abc123\""),
                    "Should contain spreadsheetId, got: " + params);
            assertTrue(params.contains("\"range\":\"Sheet1!A1:C10\""),
                    "Should contain range, got: " + params);
        }

        @Test
        void returnsSanitizedResult() {
            stub.setCannedResponse("{\"values\":[[\"a\"]]}");
            CallToolResult result = callTool("sheets_values_get", Map.of(
                    "spreadsheetId", "id1", "range", "A1"));
            assertEquals(2, result.content().size(),
                    "Read-only tool should return sanitizedResult (2 content items)");
        }

        @Test
        void requiresSpreadsheetId() {
            CallToolResult result = callTool("sheets_values_get", Map.of("range", "A1"));
            assertTrue(isError(result));
            String text = ((TextContent) result.content().get(0)).text();
            assertTrue(text.contains("spreadsheetId"),
                    "Error should mention spreadsheetId, got: " + text);
        }

        @Test
        void requiresRange() {
            CallToolResult result = callTool("sheets_values_get", Map.of("spreadsheetId", "id1"));
            assertTrue(isError(result));
            String text = ((TextContent) result.content().get(0)).text();
            assertTrue(text.contains("range"),
                    "Error should mention range, got: " + text);
        }
    }

    // ── sheets_values_update ────────────────────────────────────────

    @Nested
    class SheetsValuesUpdate {

        @Test
        void includesValueInputOptionAndValuesInBody() {
            List<List<String>> values = List.of(
                    List.of("a", "b"),
                    List.of("c", "d"));

            callTool("sheets_values_update", Map.of(
                    "spreadsheetId", "xyz789",
                    "range", "Sheet1!A1",
                    "values", values));

            List<String> args = stub.lastArgs();
            assertEquals("sheets", args.get(0));
            assertEquals("spreadsheets", args.get(1));
            assertEquals("values", args.get(2));
            assertEquals("update", args.get(3));
            assertEquals("--params", args.get(4));

            String params = args.get(5);
            assertTrue(params.contains("\"valueInputOption\":\"USER_ENTERED\""),
                    "Should include valueInputOption=USER_ENTERED, got: " + params);
            assertTrue(params.contains("\"spreadsheetId\":\"xyz789\""),
                    "Should contain spreadsheetId, got: " + params);
            assertTrue(params.contains("\"range\":\"Sheet1!A1\""),
                    "Should contain range, got: " + params);

            assertEquals("--json", args.get(6));
            String body = args.get(7);
            assertTrue(body.contains("\"values\""),
                    "Body should contain values, got: " + body);
            assertTrue(body.contains("\"a\""),
                    "Body should contain cell value 'a', got: " + body);
        }

        @Test
        void returnsPlainResult() {
            stub.setCannedResponse("{\"updatedCells\":4}");
            CallToolResult result = callTool("sheets_values_update", Map.of(
                    "spreadsheetId", "id1",
                    "range", "A1",
                    "values", List.of(List.of("x"))));
            // plainResult produces 1 content item
            assertEquals(1, result.content().size(),
                    "Write tool should return plainResult (1 content item)");
        }

        @Test
        void requiresSpreadsheetId() {
            CallToolResult result = callTool("sheets_values_update", Map.of(
                    "range", "A1", "values", List.of(List.of("x"))));
            assertTrue(isError(result));
        }

        @Test
        void requiresRange() {
            CallToolResult result = callTool("sheets_values_update", Map.of(
                    "spreadsheetId", "id1", "values", List.of(List.of("x"))));
            assertTrue(isError(result));
        }

        @Test
        void requiresValues() {
            CallToolResult result = callTool("sheets_values_update", Map.of(
                    "spreadsheetId", "id1", "range", "A1"));
            assertTrue(isError(result));
            String text = ((TextContent) result.content().get(0)).text();
            assertTrue(text.contains("values"),
                    "Error should mention values, got: " + text);
        }
    }

    // ── sheets_create ───────────────────────────────────────────────

    @Nested
    class SheetsCreate {

        @Test
        void includesTitleInBody() {
            callTool("sheets_create", Map.of("title", "My Sheet"));

            List<String> args = stub.lastArgs();
            assertEquals("sheets", args.get(0));
            assertEquals("spreadsheets", args.get(1));
            assertEquals("create", args.get(2));
            assertEquals("--json", args.get(3));

            String body = args.get(4);
            assertTrue(body.contains("\"title\":\"My Sheet\""),
                    "Body should contain title, got: " + body);
            assertTrue(body.contains("\"properties\""),
                    "Body should wrap title in properties, got: " + body);
        }

        @Test
        void returnsPlainResult() {
            stub.setCannedResponse("{\"spreadsheetId\":\"new123\"}");
            CallToolResult result = callTool("sheets_create", Map.of("title", "Test"));
            assertEquals(1, result.content().size(),
                    "Write tool should return plainResult (1 content item)");
        }

        @Test
        void requiresTitle() {
            CallToolResult result = callTool("sheets_create", Map.of());
            assertTrue(isError(result));
            String text = ((TextContent) result.content().get(0)).text();
            assertTrue(text.contains("title"),
                    "Error should mention title, got: " + text);
        }
    }

    // ── Error handling ──────────────────────────────────────────────

    @Nested
    class ErrorHandling {

        @Test
        void returnsErrorOnGwsFailure() {
            stub.setFailWith(new IOException("gws command failed (exit 1): auth expired"));
            CallToolResult result = callTool("sheets_values_get", Map.of(
                    "spreadsheetId", "id1", "range", "A1"));
            assertTrue(isError(result));
            String text = ((TextContent) result.content().get(0)).text();
            assertTrue(text.contains("auth expired"),
                    "Error should contain stderr message, got: " + text);
        }

        @Test
        void returnsErrorOnNullArgs() {
            // Simulate null arguments from the MCP request
            CallToolResult result = callToolRaw("sheets_values_get", null);
            assertTrue(isError(result));
        }
    }

    // ── Type validation ─────────────────────────────────────────────

    @Nested
    class TypeValidation {

        @Test
        void requireStringRejectsNonStringTypes() {
            // Integer should not be accepted as a string parameter
            CallToolResult result = callTool("sheets_values_get", Map.of(
                    "spreadsheetId", 12345,
                    "range", "A1"));
            assertTrue(isError(result), "Integer should be rejected as spreadsheetId");
        }

        @Test
        void requireStringRejectsBooleanTypes() {
            CallToolResult result = callTool("sheets_create", Map.of(
                    "title", true));
            assertTrue(isError(result), "Boolean should be rejected as title");
        }

        @Test
        void valuesParameterRejectsNull() {
            CallToolResult result = callTool("sheets_values_update", Map.of(
                    "spreadsheetId", "id1",
                    "range", "A1"));
            assertTrue(isError(result));
            String text = ((TextContent) result.content().get(0)).text();
            assertTrue(text.contains("values"), "Error should mention values");
        }
    }

    // ── Test helpers ────────────────────────────────────────────────

    private CallToolResult callTool(String toolName, Map<String, Object> args) {
        return callToolRaw(toolName, args);
    }

    private CallToolResult callToolRaw(String toolName, Map<String, Object> args) {
        SyncToolSpecification spec = specs.stream()
                .filter(s -> s.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, args);
        return spec.callHandler().apply(null, request);
    }

    private static boolean isError(CallToolResult result) {
        return result.isError() != null && result.isError();
    }
}
