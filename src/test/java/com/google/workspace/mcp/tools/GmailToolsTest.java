package com.google.workspace.mcp.tools;

import com.google.workspace.mcp.GwsClient;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GmailToolsTest {

    // ── StubGwsClient ─────────────────────────────────────────────

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

    // ── Helpers ────────────────────────────────────────────────────

    private CallToolResult call(SyncToolSpecification spec, Map<String, Object> args) {
        CallToolRequest request = new CallToolRequest(spec.tool().name(), args);
        return spec.callHandler().apply(null, request);
    }

    private SyncToolSpecification findTool(GmailTools tools, String name) {
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
        void returns5Tools() {
            GmailTools gmail = new GmailTools(new StubGwsClient());
            assertEquals(5, gmail.tools().size());
        }

        @Test
        void toolNamesAreCorrect() {
            GmailTools gmail = new GmailTools(new StubGwsClient());
            List<String> names = gmail.tools().stream()
                    .map(t -> t.tool().name())
                    .toList();
            assertEquals(List.of(
                    "gmail_messages_list",
                    "gmail_messages_get",
                    "gmail_labels_list",
                    "gmail_messages_send",
                    "gmail_drafts_create"
            ), names);
        }

        @Test
        void readOnlyTools_haveReadOnlyAnnotation() {
            GmailTools gmail = new GmailTools(new StubGwsClient());
            List<String> readOnlyNames = List.of("gmail_messages_list", "gmail_messages_get", "gmail_labels_list");
            for (SyncToolSpecification spec : gmail.tools()) {
                if (readOnlyNames.contains(spec.tool().name())) {
                    assertTrue(spec.tool().annotations().readOnlyHint(),
                            spec.tool().name() + " should be read-only");
                }
            }
        }

        @Test
        void writeTools_areNotReadOnly() {
            GmailTools gmail = new GmailTools(new StubGwsClient());
            List<String> writeNames = List.of("gmail_messages_send", "gmail_drafts_create");
            for (SyncToolSpecification spec : gmail.tools()) {
                if (writeNames.contains(spec.tool().name())) {
                    assertFalse(spec.tool().annotations().readOnlyHint(),
                            spec.tool().name() + " should not be read-only");
                }
            }
        }

        @Test
        void allTools_haveDescriptions() {
            GmailTools gmail = new GmailTools(new StubGwsClient());
            for (SyncToolSpecification spec : gmail.tools()) {
                assertNotNull(spec.tool().description(), spec.tool().name() + " should have a description");
                assertFalse(spec.tool().description().isBlank(), spec.tool().name() + " description should not be blank");
            }
        }

        @Test
        void allTools_haveInputSchemas() {
            GmailTools gmail = new GmailTools(new StubGwsClient());
            for (SyncToolSpecification spec : gmail.tools()) {
                assertNotNull(spec.tool().inputSchema(), spec.tool().name() + " should have an input schema");
                assertEquals("object", spec.tool().inputSchema().type());
            }
        }
    }

    // ── gmail_messages_list ─────────────────────────────────────

    @Nested
    class MessagesListTests {

        @Test
        void invokesCorrectGwsCommand() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"messages\":[]}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_list");

            call(spec, null);

            assertEquals("gmail", stub.lastArgs.get(0));
            assertEquals("users", stub.lastArgs.get(1));
            assertEquals("messages", stub.lastArgs.get(2));
            assertEquals("list", stub.lastArgs.get(3));
            assertEquals("--params", stub.lastArgs.get(4));
        }

        @Test
        void passesUserId() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"messages\":[]}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_list");

            call(spec, null);

            String params = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(params.contains("\"userId\":\"me\""), "Should include userId=me");
        }

        @Test
        void defaultMaxResultsIs10() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"messages\":[]}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_list");

            call(spec, null);

            String params = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(params.contains("\"maxResults\":10"),
                    "Default maxResults should be 10, got: " + params);
        }

        @Test
        void respectsCustomMaxResults() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"messages\":[]}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_list");

            call(spec, Map.of("maxResults", 25));

            String params = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(params.contains("\"maxResults\":25"),
                    "Should use custom maxResults, got: " + params);
        }

        @Test
        void includesQueryWhenProvided() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"messages\":[]}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_list");

            call(spec, Map.of("query", "from:test@example.com"));

            String params = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(params.contains("\"q\":\"from:test@example.com\""),
                    "Should include query as 'q' param, got: " + params);
        }

        @Test
        void omitsQueryWhenNotProvided() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"messages\":[]}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_list");

            call(spec, null);

            String params = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertFalse(params.contains("\"q\""), "Should not include q when no query provided");
        }

        @Test
        void returnsSanitizedResult() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"messages\":[{\"id\":\"abc\"}]}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_list");

            CallToolResult result = call(spec, null);

            assertFalse(result.isError());
            assertEquals(2, result.content().size(), "sanitizedResult produces 2 content items");
            assertTrue(textContent(result, 0).contains("SECURITY CONTEXT"));
            assertTrue(textContent(result, 1).contains("{\"messages\":[{\"id\":\"abc\"}]}"));
        }

        @Test
        void returnsErrorOnFailure() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("network timeout");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_list");

            CallToolResult result = call(spec, null);

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_messages_list failed"));
            assertTrue(textContent(result, 0).contains("network timeout"));
        }
    }

    // ── gmail_messages_get ──────────────────────────────────────

    @Nested
    class MessagesGetTests {

        @Test
        void invokesCorrectGwsCommand() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"id\":\"msg123\"}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_get");

            call(spec, Map.of("messageId", "msg123"));

            assertEquals("gmail", stub.lastArgs.get(0));
            assertEquals("users", stub.lastArgs.get(1));
            assertEquals("messages", stub.lastArgs.get(2));
            assertEquals("get", stub.lastArgs.get(3));
        }

        @Test
        void passesMessageIdAndFormat() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"id\":\"msg123\"}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_get");

            call(spec, Map.of("messageId", "msg123"));

            String params = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(params.contains("\"userId\":\"me\""));
            assertTrue(params.contains("\"id\":\"msg123\""));
            assertTrue(params.contains("\"format\":\"full\""));
        }

        @Test
        void returnsSanitizedResult() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"id\":\"msg123\",\"snippet\":\"Hello\"}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_get");

            CallToolResult result = call(spec, Map.of("messageId", "msg123"));

            assertFalse(result.isError());
            assertEquals(2, result.content().size(), "sanitizedResult produces 2 content items");
        }

        @Test
        void failsWhenMessageIdMissing() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_get");

            CallToolResult result = call(spec, Map.of());

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'messageId' is required"));
        }

        @Test
        void failsWhenMessageIdBlank() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_get");

            CallToolResult result = call(spec, Map.of("messageId", "   "));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'messageId' is required"));
        }

        @Test
        void returnsErrorOnFailure() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("not found");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_get");

            CallToolResult result = call(spec, Map.of("messageId", "abc"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_messages_get failed"));
        }
    }

    // ── gmail_labels_list ───────────────────────────────────────

    @Nested
    class LabelsListTests {

        @Test
        void invokesCorrectGwsCommand() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"labels\":[]}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_labels_list");

            call(spec, null);

            assertEquals("gmail", stub.lastArgs.get(0));
            assertEquals("users", stub.lastArgs.get(1));
            assertEquals("labels", stub.lastArgs.get(2));
            assertEquals("list", stub.lastArgs.get(3));
        }

        @Test
        void passesUserId() {
            StubGwsClient stub = new StubGwsClient();
            stub.cannedResponse = "{\"labels\":[]}";
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_labels_list");

            call(spec, null);

            String params = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(params.contains("\"userId\":\"me\""));
        }

        @Test
        void returnsPlainResult() {
            StubGwsClient stub = new StubGwsClient();
            String response = "{\"labels\":[{\"id\":\"INBOX\",\"name\":\"INBOX\"}]}";
            stub.cannedResponse = response;
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_labels_list");

            CallToolResult result = call(spec, null);

            assertFalse(result.isError());
            assertEquals(1, result.content().size(), "plainResult produces 1 content item");
            assertEquals(response, textContent(result, 0));
        }

        @Test
        void returnsErrorOnFailure() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("auth expired");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_labels_list");

            CallToolResult result = call(spec, null);

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_labels_list failed"));
            assertTrue(textContent(result, 0).contains("auth expired"));
        }
    }

    // ── gmail_messages_send ─────────────────────────────────────

    @Nested
    class MessagesSendTests {

        @Test
        void usesHelperCommand() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            call(spec, Map.of("to", "user@example.com", "subject", "Test", "body", "Body"));

            assertEquals("gmail", stub.lastArgs.get(0));
            assertEquals("+send", stub.lastArgs.get(1));
        }

        @Test
        void includesRequiredFlags() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            call(spec, Map.of("to", "user@example.com", "subject", "Test Subject", "body", "Hello World"));

            assertContainsFlag(stub.lastArgs, "--to", "user@example.com");
            assertContainsFlag(stub.lastArgs, "--subject", "Test Subject");
            assertContainsFlag(stub.lastArgs, "--body", "Hello World");
        }

        @Test
        void includesCcWhenProvided() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B", "cc", "cc@example.com"));

            assertContainsFlag(stub.lastArgs, "--cc", "cc@example.com");
        }

        @Test
        void includesBccWhenProvided() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B", "bcc", "bcc@example.com"));

            assertContainsFlag(stub.lastArgs, "--bcc", "bcc@example.com");
        }

        @Test
        void omitsCcWhenNotProvided() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            assertFalse(stub.lastArgs.contains("--cc"), "Should not include --cc when not provided");
        }

        @Test
        void omitsBccWhenNotProvided() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            assertFalse(stub.lastArgs.contains("--bcc"), "Should not include --bcc when not provided");
        }

        @Test
        void returnsPlainResult() {
            StubGwsClient stub = new StubGwsClient();
            String response = "{\"id\":\"sent1\",\"labelIds\":[\"SENT\"]}";
            stub.cannedResponse = response;
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            assertFalse(result.isError());
            assertEquals(1, result.content().size(), "plainResult produces 1 content item");
            assertEquals(response, textContent(result, 0));
        }

        @Test
        void failsWhenToMissing() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            CallToolResult result = call(spec, Map.of("subject", "S", "body", "B"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'to' is required"));
        }

        @Test
        void failsWhenSubjectMissing() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "body", "B"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'subject' is required"));
        }

        @Test
        void failsWhenBodyMissing() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "subject", "S"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'body' is required"));
        }

        @Test
        void returnsErrorOnFailure() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("rate limited");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_messages_send failed"));
            assertTrue(textContent(result, 0).contains("rate limited"));
        }
    }

    // ── gmail_drafts_create ─────────────────────────────────────

    @Nested
    class DraftsCreateTests {

        @Test
        void invokesCorrectGwsCommand() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            call(spec, Map.of("to", "user@example.com", "subject", "Draft", "body", "Body"));

            assertEquals("gmail", stub.lastArgs.get(0));
            assertEquals("users", stub.lastArgs.get(1));
            assertEquals("drafts", stub.lastArgs.get(2));
            assertEquals("create", stub.lastArgs.get(3));
        }

        @Test
        void passesUserId() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            String params = stub.lastArgs.get(stub.lastArgs.indexOf("--params") + 1);
            assertTrue(params.contains("\"userId\":\"me\""));
        }

        @Test
        void buildsRfc2822MessageAndBase64UrlEncodes() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            call(spec, Map.of("to", "user@example.com", "subject", "Test Subject", "body", "Hello World"));

            String jsonBody = stub.lastArgs.get(stub.lastArgs.indexOf("--json") + 1);

            // Extract the raw value from the JSON
            String raw = extractRawFromJson(jsonBody);
            assertNotNull(raw, "Should contain raw field");

            // Decode the base64url-encoded raw field
            byte[] decoded = Base64.getUrlDecoder().decode(raw);
            String rfc2822 = new String(decoded, StandardCharsets.UTF_8);

            assertTrue(rfc2822.startsWith("To: user@example.com\r\n"), "Should start with To header");
            assertTrue(rfc2822.contains("Subject: Test Subject\r\n"), "Should contain Subject header");
            assertTrue(rfc2822.contains("Content-Type: text/plain; charset=utf-8\r\n"), "Should contain Content-Type");
            assertTrue(rfc2822.contains("\r\n\r\nHello World"), "Should have blank line before body");
        }

        @Test
        void base64UrlEncodesWithoutPadding() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            String jsonBody = stub.lastArgs.get(stub.lastArgs.indexOf("--json") + 1);
            String raw = extractRawFromJson(jsonBody);

            assertFalse(raw.contains("+"), "Should use base64url encoding (no + character)");
            assertFalse(raw.contains("/"), "Should use base64url encoding (no / character)");
            assertFalse(raw.contains("="), "Should not have padding");
        }

        @Test
        void jsonBodyHasCorrectStructure() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            String jsonBody = stub.lastArgs.get(stub.lastArgs.indexOf("--json") + 1);
            assertTrue(jsonBody.contains("\"message\""), "Should contain 'message' key");
            assertTrue(jsonBody.contains("\"raw\""), "Should contain 'raw' key inside message");
        }

        @Test
        void returnsPlainResult() {
            StubGwsClient stub = new StubGwsClient();
            String response = "{\"id\":\"draft1\",\"message\":{\"id\":\"msg1\"}}";
            stub.cannedResponse = response;
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            assertFalse(result.isError());
            assertEquals(1, result.content().size(), "plainResult produces 1 content item");
            assertEquals(response, textContent(result, 0));
        }

        @Test
        void failsWhenToMissing() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            CallToolResult result = call(spec, Map.of("subject", "S", "body", "B"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'to' is required"));
        }

        @Test
        void failsWhenSubjectMissing() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "body", "B"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'subject' is required"));
        }

        @Test
        void failsWhenBodyMissing() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "subject", "S"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("'body' is required"));
        }

        @Test
        void failsWhenAllArgsMissing() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            CallToolResult result = call(spec, null);

            assertTrue(result.isError());
        }

        @Test
        void returnsErrorOnFailure() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("quota exceeded");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_drafts_create failed"));
            assertTrue(textContent(result, 0).contains("quota exceeded"));
        }

        @Test
        void sanitizesSubjectWithCrLf() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            call(spec, Map.of("to", "a@b.com", "subject", "Test\r\nBcc: attacker@evil.com", "body", "B"));

            String jsonBody = stub.lastArgs.get(stub.lastArgs.indexOf("--json") + 1);
            String raw = extractRawFromJson(jsonBody);
            byte[] decoded = Base64.getUrlDecoder().decode(raw);
            String rfc2822 = new String(decoded, StandardCharsets.UTF_8);

            // Subject should be sanitized - no injected Bcc header
            assertFalse(rfc2822.contains("\r\nBcc:"),
                    "CRLF injection in subject should be stripped");
            assertTrue(rfc2822.contains("Subject: TestBcc: attacker@evil.com"),
                    "Stripped subject should be inline, got: " + rfc2822);
        }

        @Test
        void sanitizesToHeaderWithCrLf() {
            StubGwsClient stub = new StubGwsClient();
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            call(spec, Map.of("to", "a@b.com\r\nBcc: attacker@evil.com", "subject", "S", "body", "B"));

            String jsonBody = stub.lastArgs.get(stub.lastArgs.indexOf("--json") + 1);
            String raw = extractRawFromJson(jsonBody);
            byte[] decoded = Base64.getUrlDecoder().decode(raw);
            String rfc2822 = new String(decoded, StandardCharsets.UTF_8);

            assertFalse(rfc2822.contains("\r\nBcc:"),
                    "CRLF injection in To header should be stripped");
        }
    }

    // ── Helper method tests ─────────────────────────────────────

    @Nested
    class HelperTests {

        @Test
        void safeArgs_returnsEmptyMapForNullArguments() {
            CallToolRequest req = new CallToolRequest("test", null);
            Map<String, Object> args = GmailTools.safeArgs(req);
            assertNotNull(args);
            assertTrue(args.isEmpty());
        }

        @Test
        void safeArgs_returnsArgumentsWhenPresent() {
            Map<String, Object> original = Map.of("key", "value");
            CallToolRequest req = new CallToolRequest("test", original);
            assertSame(original, GmailTools.safeArgs(req));
        }

        @Test
        void requireString_returnsValueWhenPresent() {
            assertEquals("test", GmailTools.requireString(Map.of("name", "test"), "name"));
        }

        @Test
        void requireString_throwsWhenMissing() {
            assertThrows(IllegalArgumentException.class,
                    () -> GmailTools.requireString(Map.of(), "name"));
        }

        @Test
        void requireString_throwsWhenBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> GmailTools.requireString(Map.of("name", "   "), "name"));
        }

        @Test
        void requireString_throwsForNonString() {
            assertThrows(IllegalArgumentException.class,
                    () -> GmailTools.requireString(Map.of("name", 42), "name"));
        }

        @Test
        void parsePageSize_defaultsTo10ForNull() {
            assertEquals(10, GmailTools.parsePageSize(null));
        }

        @Test
        void parsePageSize_acceptsValidNumber() {
            assertEquals(50, GmailTools.parsePageSize(50));
        }

        @Test
        void parsePageSize_parsesStringValue() {
            assertEquals(25, GmailTools.parsePageSize("25"));
        }

        @Test
        void parsePageSize_defaultsTo10ForInvalidString() {
            assertEquals(10, GmailTools.parsePageSize("not-a-number"));
        }

        @Test
        void parsePageSize_clampsTo1ForZero() {
            assertEquals(1, GmailTools.parsePageSize(0));
        }

        @Test
        void parsePageSize_clampsTo100ForLargeValue() {
            assertEquals(100, GmailTools.parsePageSize(200));
        }
    }

    // ── sanitizeHeader ─────────────────────────────────────────

    @Nested
    class SanitizeHeaderTests {

        @Test
        void stripsCarriageReturn() {
            assertEquals("TestValue", GmailTools.sanitizeHeader("Test\rValue"));
        }

        @Test
        void stripsLineFeed() {
            assertEquals("TestValue", GmailTools.sanitizeHeader("Test\nValue"));
        }

        @Test
        void stripsCrLfSequence() {
            assertEquals("TestValue", GmailTools.sanitizeHeader("Test\r\nValue"));
        }

        @Test
        void preventsHeaderInjection() {
            String malicious = "Test\r\nBcc: attacker@evil.com";
            String sanitized = GmailTools.sanitizeHeader(malicious);
            assertFalse(sanitized.contains("\r"), "Should not contain CR");
            assertFalse(sanitized.contains("\n"), "Should not contain LF");
            // After stripping, the injected header becomes inline text: "TestBcc: attacker@evil.com"
            // This is safe because without CRLF it can't be parsed as a separate header
            assertEquals("TestBcc: attacker@evil.com", sanitized);
        }

        @Test
        void leavesCleanHeaderUnchanged() {
            assertEquals("Normal Subject", GmailTools.sanitizeHeader("Normal Subject"));
        }

        @Test
        void handlesEmptyString() {
            assertEquals("", GmailTools.sanitizeHeader(""));
        }
    }

    // ── Error handling ──────────────────────────────────────────

    @Nested
    class ErrorHandlingTests {

        @Test
        void messagesListErrorIncludesToolName() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("connection refused");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_list");

            CallToolResult result = call(spec, null);

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_messages_list"));
        }

        @Test
        void messagesGetErrorIncludesToolName() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("timeout");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_get");

            CallToolResult result = call(spec, Map.of("messageId", "abc"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_messages_get"));
        }

        @Test
        void labelsListErrorIncludesToolName() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("forbidden");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_labels_list");

            CallToolResult result = call(spec, null);

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_labels_list"));
        }

        @Test
        void messagesSendErrorIncludesToolName() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("invalid recipient");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_messages_send");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_messages_send"));
        }

        @Test
        void draftsCreateErrorIncludesToolName() {
            StubGwsClient stub = new StubGwsClient();
            stub.failWith = new IOException("storage full");
            GmailTools gmail = new GmailTools(stub);
            SyncToolSpecification spec = findTool(gmail, "gmail_drafts_create");

            CallToolResult result = call(spec, Map.of("to", "a@b.com", "subject", "S", "body", "B"));

            assertTrue(result.isError());
            assertTrue(textContent(result, 0).contains("gmail_drafts_create"));
        }
    }

    // ── Test utilities ──────────────────────────────────────────

    private static String extractRawFromJson(String json) {
        int rawIdx = json.indexOf("\"raw\":\"");
        if (rawIdx == -1) return null;
        int start = rawIdx + 7;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private static void assertContainsFlag(List<String> args, String flag, String value) {
        int idx = args.indexOf(flag);
        assertTrue(idx != -1, "Should contain " + flag + " flag, args: " + args);
        assertTrue(idx + 1 < args.size(), flag + " should have a value after it");
        assertEquals(value, args.get(idx + 1),
                flag + " value should be '" + value + "' but was '" + args.get(idx + 1) + "'");
    }
}
