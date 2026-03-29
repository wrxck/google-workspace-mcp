package com.google.workspace.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.workspace.mcp.tools.*;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GoogleWorkspaceMcpServer {

    private static final Logger log = LoggerFactory.getLogger(GoogleWorkspaceMcpServer.class);
    static final String SERVER_NAME = "google-workspace";
    static final String SERVER_VERSION = resolveVersion();

    private static String resolveVersion() {
        String v = GoogleWorkspaceMcpServer.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--install".equals(args[0])) {
            String claudeBinary = null;
            for (int i = 1; i < args.length - 1; i++) {
                if ("--claude-binary".equals(args[i])) {
                    claudeBinary = args[i + 1];
                    break;
                }
            }
            GwsAuth.install(claudeBinary);
            return;
        }

        if (args.length > 0 && "--auth".equals(args[0])) {
            GwsAuth.authenticate();
            return;
        }

        startServer();
    }

    static void startServer() {
        GwsClient client = new GwsClient();

        if (!client.isAvailable()) {
            System.err.println("Error: gws CLI not found on PATH.");
            System.err.println("Install it with: npm install -g @googleworkspace/cli");
            System.err.println("Then run: java -jar google-workspace-mcp.jar --install");
            System.exit(1);
        }

        List<SyncToolSpecification> allTools = collectTools(client);
        log.info("Registered {} tools", allTools.size());

        var transport = new StdioServerTransportProvider(
                new JacksonMcpJsonMapper(new ObjectMapper()));

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .tools(allTools)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            server.close();
        }));

        log.info("Google Workspace MCP server started");
    }

    static List<SyncToolSpecification> collectTools(GwsClient client) {
        List<SyncToolSpecification> allTools = new ArrayList<>();
        allTools.addAll(new DriveTools(client).tools());
        allTools.addAll(new CalendarTools(client).tools());
        allTools.addAll(new SheetsTools(client).tools());
        allTools.addAll(new DocsTools(client).tools());
        allTools.addAll(new GmailTools(client).tools());
        allTools.addAll(new ChatTools(client).tools());
        allTools.addAll(AdminTools.tools(client));
        return allTools;
    }

    static String parseClaudeBinary(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--claude-binary".equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }
}
