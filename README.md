# MCP Inspector Plugin

IntelliJ/PyCharm plugin that connects to Model Context Protocol (MCP) servers, lists available tools, and allows users to invoke tools with parameters.

## Features

- **Connection Management**: Connect, disconnect, restart connection with MCP servers
- **Tool Discovery**: Browse available tools from connected MCP servers
- **Smart Parameter Input**: Two input modes for maximum convenience:
  - **Simple Form Mode**: Auto-generated forms based on tool schema with proper field types 
  - **JSON Mode**: Direct JSON input for advanced users
- **Tool Invocation**: Execute tools with custom parameters and view results
- **History**: Check what tools were executed and what parameters used 
- **Notifications**: List server messages
- **Modern UI**: Built with Compose
- **Auto-reload mode** Edit MCP server and see changes in real time

## Prerequisites

- IntelliJ IDEA or PyCharm (2023.3.2 or later)
- Java 17 or later
- Python 3.8+ (for running the MCP server)

## Quick Start

### 1. Running the MCP Server

The project includes a simple MCP server for testing. Install dependencies and start the server:

```bash
# Install Python dependencies
pip install mcp fastmcp python-dotenv

# Start the server (choose one method)
./run-server-watch.sh                    # With auto-reload
```

The server will start on `http://localhost:8050` with a simple calculator tool.

### 2. Building and Running the Plugin

#### Option A: Run script for a plugin

```bash
./run-plugin.sh
```

### 3. Development Mode 

Use the development script to start both server and plugin:

```bash
./dev-start.sh
```

This script will:
- Start the MCP server with auto-reload
- Build and run the plugin in IntelliJ IDEA
- Provide an interactive menu for different startup options

## Using the Plugin

1. **Open the Tool Window**: Find "MCP Inspector Lite" in the right sidebar
2. **Connect to Server**: Enter `http://localhost:8050/sse` and click "Connect"
3. **Browse Tools**: View available tools in the left pane
4. **Execute Tools**: Select a tool, enter parameters, and click "Execute Tool"

## Development

### Project Structure

```
mcp-plugin/
├── src/main/
│   ├── kotlin/com/example/mcpinspector/
│   │   ├── model/
│   │   │   └── McpModels.kt           # MCP protocol data models
│   │   ├── mcp/
│   │   │   └── McpClient.kt           # MCP client for server communication
│   │   └── ui/
│   │       ├── McpToolWindowFactory.kt  # Tool window factory
│   │       ├── McpInspectorApp.kt       # Main Compose application
│   │       ├── ConnectionPane.kt        # Connection management UI
│   │       ├── ToolsPane.kt            # Tools list UI
│   │       ├── DetailsPane.kt          # Tool details and execution UI
│   │       ├── HistoryPane.kt          # Execution history UI
│   │       └── NotificationsPane.kt    # Server notifications UI
│   └── resources/
│       ├── META-INF/plugin.xml         # Plugin configuration
│       └── icons/mcp-icon.svg          # Plugin icon
├── simple-server-setup/                # MCP server for testing
│   ├── server.py                       # Main MCP server
│   ├── watch_server.py                 # Auto-reload server wrapper
│   ├── client-*.py                     # Example clients
│   └── README.md                       # Server documentation
├── build.gradle.kts                    # Build configuration
├── dev-start.sh                        # Development environment script
├── run-plugin.sh                       # Plugin runner script
└── run-server-watch.sh                 # Server with auto-reload script
```

### Development Scripts

- `./dev-start.sh` - Start both server and plugin for development
- `./run-plugin.sh` - Build and run plugin 
- `./run-server-watch.sh` - Start server with auto-reload
