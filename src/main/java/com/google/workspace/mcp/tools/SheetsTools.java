package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.ContentSanitizer;
import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tools for Google Sheets operations.
 *
 * <ul>
 *   <li>sheets_list — List spreadsheets via Drive query</li>
 *   <li>sheets_values_get — Read a cell range</li>
 *   <li>sheets_values_update — Write to a cell range</li>
 *   <li>sheets_create — Create a spreadsheet</li>
 * </ul>
 */
public final class SheetsTools {

    private final GwsClient client;

    public SheetsTools(GwsClient client) {
        this.client = client;
    }

    public List<SyncToolSpecification> tools() {
        return List.of(sheetsList(), sheetsValuesGet(), sheetsValuesUpdate(), sheetsCreate());
    }

    // ── sheets_list ─────────────────────────────────────────────────

    private SyncToolSpecification sheetsList() {
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("sheets_list")
                        .description("List spreadsheets. Returns spreadsheet names and IDs.")
                        .inputSchema(schema(Map.of(
                                "pageSize", Map.of("type", "integer",
                                        "description", "Number of results to return (default 20)")
                        ), List.of()))
                        .annotations(new McpSchema.ToolAnnotations(
                                "List spreadsheets", true, false, true, true, null))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        int pageSize = parsePageSize(args.get("pageSize"));

                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("q", "mimeType='application/vnd.google-apps.spreadsheet'");
                        params.put("pageSize", pageSize);

                        List<String> cmd = List.of("drive", "files", "list",
                                "--params", GwsClient.paramsJson(params));
                        String json = client.executeJson(cmd);
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // ── sheets_values_get ───────────────────────────────────────────

    private SyncToolSpecification sheetsValuesGet() {
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("sheets_values_get")
                        .description("Read a cell range from a spreadsheet.")
                        .inputSchema(schema(Map.of(
                                "spreadsheetId", Map.of("type", "string",
                                        "description", "The ID of the spreadsheet"),
                                "range", Map.of("type", "string",
                                        "description", "The A1 notation range to read (e.g. Sheet1!A1:C10)")
                        ), List.of("spreadsheetId", "range")))
                        .annotations(new McpSchema.ToolAnnotations(
                                "Read cell range", true, false, true, true, null))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String spreadsheetId = requireString(args, "spreadsheetId");
                        String range = requireString(args, "range");

                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("spreadsheetId", spreadsheetId);
                        params.put("range", range);

                        List<String> cmd = List.of("sheets", "spreadsheets", "values", "get",
                                "--params", GwsClient.paramsJson(params));
                        String json = client.executeJson(cmd);
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // ── sheets_values_update ────────────────────────────────────────

    private SyncToolSpecification sheetsValuesUpdate() {
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("sheets_values_update")
                        .description("Write values to a cell range in a spreadsheet.")
                        .inputSchema(schema(Map.of(
                                "spreadsheetId", Map.of("type", "string",
                                        "description", "The ID of the spreadsheet"),
                                "range", Map.of("type", "string",
                                        "description", "The A1 notation range to write (e.g. Sheet1!A1)"),
                                "values", Map.of("type", "array",
                                        "description", "2D array of cell values, e.g. [[\"a\",\"b\"],[\"c\",\"d\"]]",
                                        "items", Map.of("type", "array",
                                                "items", Map.of("type", "string")))
                        ), List.of("spreadsheetId", "range", "values")))
                        .annotations(new McpSchema.ToolAnnotations(
                                "Write cell range", false, false, true, true, null))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String spreadsheetId = requireString(args, "spreadsheetId");
                        String range = requireString(args, "range");
                        Object values = args.get("values");
                        if (values == null) {
                            throw new IllegalArgumentException("Missing required parameter: values");
                        }

                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("spreadsheetId", spreadsheetId);
                        params.put("range", range);
                        params.put("valueInputOption", "USER_ENTERED");

                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("values", values);

                        List<String> cmd = List.of("sheets", "spreadsheets", "values", "update",
                                "--params", GwsClient.paramsJson(params),
                                "--json", GwsClient.bodyJson(body));
                        String json = client.executeJson(cmd);
                        return ContentSanitizer.plainResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // ── sheets_create ───────────────────────────────────────────────

    private SyncToolSpecification sheetsCreate() {
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("sheets_create")
                        .description("Create a new spreadsheet.")
                        .inputSchema(schema(Map.of(
                                "title", Map.of("type", "string",
                                        "description", "Title of the new spreadsheet")
                        ), List.of("title")))
                        .annotations(new McpSchema.ToolAnnotations(
                                "Create spreadsheet", false, false, false, true, null))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String title = requireString(args, "title");

                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("properties", Map.of("title", title));

                        List<String> cmd = List.of("sheets", "spreadsheets", "create",
                                "--json", GwsClient.bodyJson(body));
                        String json = client.executeJson(cmd);
                        return ContentSanitizer.plainResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult(e.getMessage());
                    }
                })
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static Map<String, Object> safeArgs(McpSchema.CallToolRequest request) {
        return request.arguments() != null ? request.arguments() : Map.of();
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalArgumentException("Missing required parameter: " + key);
    }

    private static int parsePageSize(Object raw) {
        if (raw == null) return 20;
        if (raw instanceof Number n) return Math.max(1, Math.min(n.intValue(), 100));
        try {
            int val = Integer.parseInt(raw.toString());
            return Math.max(1, Math.min(val, 100));
        } catch (NumberFormatException e) {
            return 20;
        }
    }

    @SuppressWarnings("unchecked")
    private static McpSchema.JsonSchema schema(Map<String, ?> properties, List<String> required) {
        return new McpSchema.JsonSchema("object",
                (Map<String, Object>) (Map<?, ?>) properties,
                required.isEmpty() ? null : required,
                false, null, null);
    }
}
