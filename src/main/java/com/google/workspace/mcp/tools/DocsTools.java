package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.ContentSanitizer;
import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Docs MCP tools: get, create, list.
 */
public final class DocsTools {

    private final GwsClient client;

    public DocsTools(GwsClient client) {
        this.client = client;
    }

    public List<SyncToolSpecification> tools() {
        return List.of(docsGet(), docsCreate(), docsList());
    }

    // ── docs_get ────────────────────────────────────────────────

    private SyncToolSpecification docsGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("documentId", Map.of("type", "string", "description", "The ID of the document to retrieve"));

        Tool tool = Tool.builder()
                .name("docs_get")
                .description("Get Google Docs document content by ID")
                .inputSchema(new JsonSchema("object", properties, List.of("documentId"), null, null, null))
                .annotations(new ToolAnnotations("Get document content", true, false, true, true, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handleDocsGet(request.arguments()))
                .build();
    }

    private CallToolResult handleDocsGet(Map<String, Object> args) {
        try {
            String documentId = requireString(args, "documentId");
            Map<String, Object> params = Map.of("documentId", documentId);
            List<String> command = safeArgs("docs", "documents", "get", "--params", GwsClient.paramsJson(params));
            String json = client.executeJson(command);
            return ContentSanitizer.sanitizedResult(json);
        } catch (Exception e) {
            return ContentSanitizer.errorResult(e.getMessage());
        }
    }

    // ── docs_create ─────────────────────────────────────────────

    private SyncToolSpecification docsCreate() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of("type", "string", "description", "The title of the new document"));

        Tool tool = Tool.builder()
                .name("docs_create")
                .description("Create a new Google Docs document")
                .inputSchema(new JsonSchema("object", properties, List.of("title"), null, null, null))
                .annotations(new ToolAnnotations("Create a document", false, false, false, true, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handleDocsCreate(request.arguments()))
                .build();
    }

    private CallToolResult handleDocsCreate(Map<String, Object> args) {
        try {
            String title = requireString(args, "title");
            Map<String, Object> body = Map.of("title", title);
            List<String> command = safeArgs("docs", "documents", "create", "--json", GwsClient.bodyJson(body));
            String json = client.executeJson(command);
            return ContentSanitizer.plainResult(json);
        } catch (Exception e) {
            return ContentSanitizer.errorResult(e.getMessage());
        }
    }

    // ── docs_list ───────────────────────────────────────────────

    private SyncToolSpecification docsList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("pageSize", Map.of("type", "integer", "description", "Maximum number of documents to return (default 20)"));

        Tool tool = Tool.builder()
                .name("docs_list")
                .description("List Google Docs documents")
                .inputSchema(new JsonSchema("object", properties, null, null, null, null))
                .annotations(new ToolAnnotations("List documents", true, false, true, true, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handleDocsList(request.arguments()))
                .build();
    }

    private CallToolResult handleDocsList(Map<String, Object> args) {
        try {
            int pageSize = parsePageSize(args, 20);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("q", "mimeType='application/vnd.google-apps.document'");
            params.put("pageSize", pageSize);
            List<String> command = safeArgs("drive", "files", "list", "--params", GwsClient.paramsJson(params));
            String json = client.executeJson(command);
            return ContentSanitizer.sanitizedResult(json);
        } catch (Exception e) {
            return ContentSanitizer.errorResult(e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    static List<String> safeArgs(String... parts) {
        List<String> args = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                args.add(part);
            }
        }
        return args;
    }

    static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalArgumentException("Missing required argument: " + key);
    }

    static int parsePageSize(Map<String, Object> args, int defaultValue) {
        Object value = args.get("pageSize");
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
