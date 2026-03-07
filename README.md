# Google Workspace MCP Server

An [MCP (Model Context Protocol)](https://modelcontextprotocol.io/) server that exposes 28 Google Workspace tools (Drive, Calendar, Sheets, Docs, Gmail, Chat, Admin) by wrapping the [`gws` CLI](https://github.com/nicholasgasior/gws) as a subprocess. No direct API integration — all operations go through the gws binary.

## Table of contents

- [How it works](#how-it-works)
- [Tools (28)](#tools-28)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Security](#security)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [License](#license)

## How it works

```
Claude Code <-> (stdio) <-> google-workspace-mcp.jar
                              |
                         GwsClient (ProcessBuilder)
                              |
                         gws CLI binary (@googleworkspace/cli)
                              |
                         Google Workspace APIs
```

Each MCP tool call translates arguments into a `gws` command-line invocation, executes it as a subprocess, and returns the JSON response. Auth is delegated entirely to `gws auth`.

## Tools (28)

### Drive (6)

| Tool | Description | Annotations |
|------|-------------|:-----------:|
| `drive_files_list` | List files/folders with optional search query | read-only |
| `drive_files_get` | Get file metadata by ID | read-only |
| `drive_files_download` | Download file content to local disk | read-only |
| `drive_files_upload` | Upload a file to Drive | |
| `drive_files_create_folder` | Create a folder | |
| `drive_files_delete` | Delete a file or folder | destructive |

### Calendar (4)

| Tool | Description | Annotations |
|------|-------------|:-----------:|
| `calendar_events_list` | List events with optional time range | read-only |
| `calendar_events_get` | Get event details by ID | read-only |
| `calendar_events_create` | Create a calendar event | |
| `calendar_events_delete` | Delete a calendar event | destructive |

### Sheets (4)

| Tool | Description | Annotations |
|------|-------------|:-----------:|
| `sheets_list` | List spreadsheets | read-only |
| `sheets_values_get` | Read a cell range | read-only |
| `sheets_values_update` | Write to a cell range | |
| `sheets_create` | Create a new spreadsheet | |

### Docs (3)

| Tool | Description | Annotations |
|------|-------------|:-----------:|
| `docs_get` | Get document content | read-only |
| `docs_create` | Create a new document | |
| `docs_list` | List documents | read-only |

### Gmail (5)

| Tool | Description | Annotations |
|------|-------------|:-----------:|
| `gmail_messages_list` | List/search messages | read-only |
| `gmail_messages_get` | Read a message by ID | read-only |
| `gmail_labels_list` | List labels | read-only |
| `gmail_messages_send` | Send an email | |
| `gmail_drafts_create` | Create a draft email | |

### Chat (3)

| Tool | Description | Annotations |
|------|-------------|:-----------:|
| `chat_spaces_list` | List Chat spaces | read-only |
| `chat_messages_list` | List messages in a space | read-only |
| `chat_messages_create` | Send a message to a space | |

### Admin (3)

| Tool | Description | Annotations |
|------|-------------|:-----------:|
| `admin_users_list` | List directory users via People API | read-only |
| `admin_users_get` | Get user details | read-only |
| `admin_groups_list` | List contact groups | read-only |

## Prerequisites

- **Java 21** or later
- **gws CLI** — `npm install -g @googleworkspace/cli`
- **Google Cloud project** with Workspace APIs enabled (the `--install` flow walks you through this)

## Quick start

### 1. Build

```bash
git clone https://github.com/wrxck/google-workspace-mcp.git
cd google-workspace-mcp
mvn clean package
```

This produces `target/google-workspace-mcp-1.0.0.jar` — a self-contained executable JAR.

### 2. Install

```bash
java -jar target/google-workspace-mcp-1.0.0.jar --install
```

This will:
1. Check `gws` is on your PATH
2. Run `gws auth setup` (configures Google Cloud project + enables APIs)
3. Run `gws auth login` (browser-based OAuth flow)
4. Register the MCP server with Claude Code

### 3. Use

```
> List my upcoming calendar events for this week
> Download the Q4 report from Drive
> Send an email to team@example.com about the status update
> What spreadsheets do I have?
```

## Configuration

| Flag | Description |
|------|-------------|
| `--install` | Full setup: auth + register with Claude Code |
| `--install --claude-binary /path/to/claude` | Install with custom Claude binary path |
| `--auth` | Re-authenticate only (no setup, no registration) |
| *(no flags)* | Start MCP server (normal operation) |

### Manual registration

If automatic registration fails:

```bash
claude mcp add --scope user --transport stdio google-workspace -- \
  java -jar /path/to/google-workspace-mcp-1.0.0.jar
```

## Security

- **Response sanitization** — All API responses containing untrusted content (email bodies, document text, file names, chat messages) are wrapped in unique cryptographic boundary markers. This defends against prompt injection via API responses.
- **Tool annotations** — `readOnlyHint` and `destructiveHint` are set accurately per tool, so Claude treats delete operations with appropriate caution.
- **Command injection prevention** — Arguments are passed as a list to `ProcessBuilder`, never shell-interpolated.
- **Input validation** — Required parameters are validated before subprocess execution.

## Building from source

```bash
mvn clean verify
```

This compiles, runs all 310 tests, and produces the shaded JAR.

## Project structure

```
src/main/java/com/google/workspace/mcp/
├── GoogleWorkspaceMcpServer.java  # Entry point, CLI flags, server bootstrap
├── GwsClient.java                 # Subprocess execution, JSON parsing, timeouts
├── GwsAuth.java                   # Auth delegation (gws auth setup/login)
├── ContentSanitizer.java          # Prompt injection defense (boundary markers)
└── tools/
    ├── DriveTools.java            # 6 Drive tools
    ├── CalendarTools.java         # 4 Calendar tools
    ├── SheetsTools.java           # 4 Sheets tools
    ├── DocsTools.java             # 3 Docs tools
    ├── GmailTools.java            # 5 Gmail tools
    ├── ChatTools.java             # 3 Chat tools
    └── AdminTools.java            # 3 Admin tools (People API)
```

## License

[Apache License 2.0](LICENSE)
