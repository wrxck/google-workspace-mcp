package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.ContentSanitizer;
import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class GmailTools {

    private static final int DEFAULT_MAX_RESULTS = 10;

    private final GwsClient client;

    public GmailTools(GwsClient client) {
        this.client = client;
    }

    public List<SyncToolSpecification> tools() {
        return List.of(messagesList(), messagesGet(), labelsList(), messagesSend(), draftsCreate());
    }

    private SyncToolSpecification messagesList() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("gmail_messages_list")
                        .title("List Gmail Messages")
                        .description("List or search Gmail messages. Returns message IDs and thread IDs matching the query.")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "query", Map.of("type", "string", "description", "Gmail search query (e.g. 'from:someone@example.com is:unread')"),
                                "maxResults", Map.of("type", "integer", "description", "Maximum number of messages to return (default 10)")
                        ), null, null, null, null))
                        .annotations(new ToolAnnotations("List Gmail Messages", true, false, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        int maxResults = parsePageSize(args.get("maxResults"));

                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("userId", "me");
                        params.put("maxResults", maxResults);

                        if (args.get("query") instanceof String q && !q.isBlank()) {
                            params.put("q", q);
                        }

                        String json = client.executeJson(List.of(
                                "gmail", "users", "messages", "list",
                                "--params", GwsClient.paramsJson(params)));
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("gmail_messages_list failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification messagesGet() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("gmail_messages_get")
                        .title("Get Gmail Message")
                        .description("Get the full content of a Gmail message by its ID, including headers and body.")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "messageId", Map.of("type", "string", "description", "The Gmail message ID")
                        ), List.of("messageId"), null, null, null))
                        .annotations(new ToolAnnotations("Get Gmail Message", true, false, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String messageId = requireString(args, "messageId");

                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("userId", "me");
                        params.put("id", messageId);
                        params.put("format", "full");

                        String json = client.executeJson(List.of(
                                "gmail", "users", "messages", "get",
                                "--params", GwsClient.paramsJson(params)));
                        return ContentSanitizer.sanitizedResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("gmail_messages_get failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification labelsList() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("gmail_labels_list")
                        .title("List Gmail Labels")
                        .description("List all Gmail labels (inbox, sent, custom labels, etc.).")
                        .inputSchema(new JsonSchema("object", Map.of(), null, null, null, null))
                        .annotations(new ToolAnnotations("List Gmail Labels", true, false, true, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        String json = client.executeJson(List.of(
                                "gmail", "users", "labels", "list",
                                "--params", GwsClient.paramsJson(Map.of("userId", "me"))));
                        return ContentSanitizer.plainResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("gmail_labels_list failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification messagesSend() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("gmail_messages_send")
                        .title("Send Gmail Message")
                        .description("Send an email message via Gmail.")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "to", Map.of("type", "string", "description", "Recipient email address"),
                                "subject", Map.of("type", "string", "description", "Email subject line"),
                                "body", Map.of("type", "string", "description", "Email body text"),
                                "cc", Map.of("type", "string", "description", "CC email address"),
                                "bcc", Map.of("type", "string", "description", "BCC email address")
                        ), List.of("to", "subject", "body"), null, null, null))
                        .annotations(new ToolAnnotations("Send Gmail Message", false, false, false, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String to = requireString(args, "to");
                        String subject = requireString(args, "subject");
                        String body = requireString(args, "body");

                        List<String> command = new ArrayList<>(List.of(
                                "gmail", "+send",
                                "--to", to,
                                "--subject", subject,
                                "--body", body));

                        if (args.get("cc") instanceof String cc && !cc.isBlank()) {
                            command.add("--cc");
                            command.add(cc);
                        }
                        if (args.get("bcc") instanceof String bcc && !bcc.isBlank()) {
                            command.add("--bcc");
                            command.add(bcc);
                        }

                        String json = client.executeJson(command);
                        return ContentSanitizer.plainResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("gmail_messages_send failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification draftsCreate() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("gmail_drafts_create")
                        .title("Create Gmail Draft")
                        .description("Create a Gmail draft message.")
                        .inputSchema(new JsonSchema("object", Map.of(
                                "to", Map.of("type", "string", "description", "Recipient email address"),
                                "subject", Map.of("type", "string", "description", "Email subject line"),
                                "body", Map.of("type", "string", "description", "Email body text")
                        ), List.of("to", "subject", "body"), null, null, null))
                        .annotations(new ToolAnnotations("Create Gmail Draft", false, false, false, true, false))
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = safeArgs(request);
                        String to = requireString(args, "to");
                        String subject = requireString(args, "subject");
                        String body = requireString(args, "body");

                        // Build RFC 2822 message (strip CRLF from headers to prevent injection)
                        String rfc2822 = "To: " + sanitizeHeader(to)
                                + "\r\nSubject: " + sanitizeHeader(subject)
                                + "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n" + body;
                        String encoded = Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(rfc2822.getBytes(StandardCharsets.UTF_8));

                        String json = client.executeJson(List.of(
                                "gmail", "users", "drafts", "create",
                                "--params", GwsClient.paramsJson(Map.of("userId", "me")),
                                "--json", GwsClient.bodyJson(Map.of("message", Map.of("raw", encoded)))));
                        return ContentSanitizer.plainResult(json);
                    } catch (Exception e) {
                        return ContentSanitizer.errorResult("gmail_drafts_create failed: " + e.getMessage());
                    }
                })
                .build();
    }

    static Map<String, Object> safeArgs(CallToolRequest request) {
        return request.arguments() != null ? request.arguments() : Map.of();
    }

    static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalArgumentException("'" + key + "' is required");
    }

    /**
     * Strip CR and LF characters from email header values to prevent header injection.
     */
    static String sanitizeHeader(String value) {
        return value.replace("\r", "").replace("\n", "");
    }

    static int parsePageSize(Object raw) {
        int size = DEFAULT_MAX_RESULTS;
        if (raw instanceof Number n) {
            size = n.intValue();
        } else if (raw instanceof String s) {
            try { size = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return Math.max(1, Math.min(100, size));
    }
}
