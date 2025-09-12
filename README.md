# MCP Inspector Lite

A minimal IntelliJ/PyCharm plugin in Kotlin that connects to Model Context Protocol (MCP) servers, lists available tools, and allows users to invoke tools with parameters.

## 🚀 Quick Start

### Development Setup (Separate Terminals)
```bash
# Terminal 1: Start MCP server with auto-reload
./dev-start.sh server

# Terminal 2: Start IntelliJ plugin
./dev-start.sh inspector
```

### Using the Plugin
1. Open **View → Tool Windows → MCP Inspector Lite** in the launched IDE
2. Connect to `http://localhost:3000`
3. Try the available tools (echo, calculate, server_info, etc.)

## Features

- **Connection Management**: Connect to and disconnect from MCP servers
- **Tool Discovery**: Browse available tools from connected MCP servers
- **Smart Parameter Input**: Two input modes for maximum convenience:
  - **Simple Form Mode**: Auto-generated forms based on tool schema with proper field types
  - **JSON Mode**: Direct JSON input for advanced users
- **Tool Invocation**: Execute tools with custom parameters and view results
- **History**: Check what tools were executed and what parameters used 
- **Notifications**: List server messages
- **Modern UI**: Built with Compose Multiplatform for a responsive interface
- **Three-Pane Layout**: 
  - Connection Pane: Server connection management
  - Tools Pane: Available tools listing
  - Details & Results Pane: Tool details, smart parameter input, and execution results

## Architecture

The plugin is structured with the following components:

- **MCP Client** (`McpClient.kt`): Handles JSON-RPC communication with MCP servers
- **UI Components** (`McpInspectorApp.kt`): Main Compose-based user interface
- **Parameter Input System**: Smart parameter handling with dual input modes
  - **Schema Parser** (`ParameterInputHelper.kt`): Parses JSON schema to extract field definitions
  - **Parameter Manager** (`ParameterInputHelper.kt`): Manages parameter values and JSON conversion
  - **Input Components** (`ParameterInputComponents.kt`): Compose UI components for different field types
- **Tool Window Factory** (`McpToolWindowFactory.kt`): IntelliJ plugin integration
- **Data Models** (`McpModels.kt`): MCP protocol data structures

### Running the MCP Server

#### Option 1: Using Development Scripts (Recommended)
```bash
# Start server with auto-reload (restarts when you edit server code)
./dev-start.sh server

# Or start without auto-reload
./dev-start.sh server-no-watch

# Check server status
./start-mcp-server.sh --status

# View server logs
./start-mcp-server.sh --logs

# Stop server
./start-mcp-server.sh --stop
```

#### Option 2: Manual Server Setup
```bash
# Install dependencies (optional - server uses only standard library)
cd test-server
pip install -r requirements.txt  # Only needed for test client

# Start server manually
python3 mcp_server.py --port 3000 --host localhost
```

#### Server Configuration
- **Default URL**: `http://localhost:3000`
- **Port**: `--port 3000` (customizable)
- **Host**: `--host localhost` (customizable)
- **Auto-reload**: Available with `./start-mcp-server.sh` (requires `fswatch`)

#### Prerequisites
- **Python 3.7+** (server uses only standard library)
- **fswatch** (optional, for auto-reload): `brew install fswatch`

## 🔧 Building and Running the Plugin

### Prerequisites
- **IntelliJ IDEA 2023.3+** or **PyCharm 2023.3+**
- **JDK 17 or higher**
- **Python 3.7+** (for the MCP test server)

### Available Build Commands
```bash
# Development commands
./dev-start.sh server          # Start server with auto-reload
./dev-start.sh inspector       # Start plugin
./dev-start.sh both           # Start both (legacy)
./dev-start.sh clean          # Clean build + start plugin

# Individual scripts
./start-mcp-server.sh         # Server management
./start-inspector.sh          # Plugin launcher
./run-plugin-with-server.sh   # Combined launcher

# Gradle commands
./gradlew buildPlugin         # Build plugin ZIP
./gradlew runIde             # Run in development IDE
./gradlew verifyPlugin       # Verify plugin compatibility
./gradlew test               # Run tests
./gradlew clean              # Clean build artifacts
```

## Using the Plugin

### 1. Open the MCP Inspector Lite Tool Window

- Go to **View → Tool Windows → MCP Inspector Lite**
- Or use the tool window tab (usually on the right side)

### 2. Connect to an MCP Server

1. In the Connection Pane, enter the server URL (e.g., `http://localhost:3000`)
2. Click **Connect**
3. The status indicator will show the connection state

### 3. Browse and Invoke Tools

1. Once connected, available tools will appear in the Tools Pane
2. Click on a tool to see its details in the Details & Results Pane
3. **Choose your input method**:
   - **Simple Form Mode** (default): Use auto-generated forms with proper field types
   - **JSON Mode**: Toggle the switch to enter parameters in JSON format
4. **Simple Form Mode**:
   - Fill out the form fields based on the tool's schema
   - Required fields are marked with an asterisk (*)
   - Different input types: text fields, numbers, dropdowns, switches for booleans
   - Parameter summary shows your current inputs
5. **JSON Mode**:
   - Enter parameters in JSON format (e.g., `{"text": "Hello World"}`)
   - Schema reference is shown for guidance
6. Click **Invoke Tool** to execute the tool
7. View the results in the same pane

## Development

### Project Structure

```
mcp-plugin/
├── build.gradle.kts                    # Gradle build configuration
├── dev-start.sh                        # Development quick start script
├── start-mcp-server.sh                 # MCP server launcher with auto-reload
├── start-inspector.sh                  # IntelliJ plugin launcher
├── run-plugin-with-server.sh           # Legacy: combined launcher
├── src/main/
│   ├── kotlin/com/example/mcpinspector/
│   │   ├── mcp/
│   │   │   └── McpClient.kt      # MCP protocol client
│   │   ├── model/
│   │   │   └── McpModels.kt      # Data models
│   │   └── ui/
│   │       ├── McpInspectorApp.kt           # Main Compose UI
│   │       ├── McpToolWindowFactory.kt      # Plugin integration
│   │       ├── ParameterInputHelper.kt      # Schema parsing & parameter management
│   │       └── ParameterInputComponents.kt  # Smart input UI components
│   └── resources/
│       ├── META-INF/plugin.xml   # Plugin configuration
│       └── icons/mcp-icon.svg    # Plugin icon
├── test-server/
│   ├── mcp_server.py            # Enhanced MCP test server (7 tools)
│   ├── test_mcp_server.py       # Server test client
│   ├── start_server.sh          # Simple server startup script
│   └── requirements.txt         # Python dependencies
└── README.md
```

### Key Dependencies

- **Kotlin**: Primary development language
- **Compose Multiplatform**: UI framework
- **Ktor**: HTTP client for MCP communication
- **Kotlinx Serialization**: JSON handling
- **IntelliJ Platform SDK**: Plugin framework

## MCP Protocol Support

This plugin implements basic MCP (Model Context Protocol) support:

- **JSON-RPC 2.0**: Standard request/response protocol
- **Tool Discovery**: `tools/list` method
- **Tool Invocation**: `tools/call` method
- **Initialization**: `initialize` handshake

### Supported MCP Methods

- `initialize`: Server capability negotiation
- `tools/list`: Retrieve available tools
- `tools/call`: Invoke a specific tool
