# MCP Inspector Lite

A minimal IntelliJ/PyCharm plugin in Kotlin that connects to Model Context Protocol (MCP) servers, lists available tools, and allows users to invoke tools with parameters.

## Features

- **Connection Management**: Connect to and disconnect from MCP servers
- **Tool Discovery**: Browse available tools from connected MCP servers
- **Smart Parameter Input**: Two input modes for maximum convenience:
  - **Simple Form Mode**: Auto-generated forms based on tool schema with proper field types
  - **JSON Mode**: Direct JSON input for advanced users
- **Tool Invocation**: Execute tools with custom parameters and view results
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

## Prerequisites

- IntelliJ IDEA 2023.3+ or PyCharm 2023.3+
- JDK 17 or higher
- Python 3.7+ (for running the test server)

## Development Quick Start

The project now includes convenient scripts for development workflow:

### 🚀 Recommended Development Workflow

For the best development experience, use separate terminals:

**Terminal 1 - Start MCP server with auto-reload:**
```bash
./dev-start.sh server-watch
```

**Terminal 2 - Start IntelliJ plugin:**
```bash
./dev-start.sh inspector
```

This allows you to:
- Edit server code and see changes automatically
- Restart the inspector independently
- View server logs in real-time

### 📋 Available Development Commands

```bash
./dev-start.sh <command>
```

Commands:
- `server` - Start MCP server only
- `server-watch` - Start MCP server with auto-reload (recommended for development)
- `inspector` - Start IntelliJ plugin only
- `both` - Start both server and inspector (legacy mode)
- `stop` - Stop running MCP server
- `status` - Check server status
- `logs` - Show server logs
- `clean` - Clean build and start inspector

### 🛠️ Individual Script Usage

You can also use the individual scripts directly:

**MCP Server Management:**
```bash
# Start server normally
./start-mcp-server.sh

# Start with auto-reload for development
./start-mcp-server.sh --watch

# Check server status
./start-mcp-server.sh --status

# View server logs
./start-mcp-server.sh --logs

# Stop server
./start-mcp-server.sh --stop
```

**IntelliJ Plugin:**
```bash
# Start inspector normally
./start-inspector.sh

# Start with clean build
./start-inspector.sh --clean

# Check server connection first
./start-inspector.sh --check-server
```

## MCP Test Server

The project includes a Python-based MCP server with the following tools:

- **echo**: Echoes back input text
- **add_numbers**: Adds two numbers together
- **get_time**: Returns current server time
- **reverse_string**: Reverses a string
- **server_info**: Returns server information and uptime
- **calculate**: Performs mathematical calculations
- **generate_uuid**: Generates random UUIDs

### Manual Server Setup

If you prefer to run the server manually:

```bash
cd test-server
python3 mcp_server.py --port 3000 --host localhost
```

Server options:
- `--port`: Port to run the server on (default: 3000)
- `--host`: Host to bind the server to (default: localhost)

## Building and Running the Plugin

### Quick Start (Recommended)

The easiest way to run the plugin with the test server:

```bash
./run-plugin-with-server.sh
```

This script will:
1. Start the MCP test server automatically
2. Build and run the plugin in IntelliJ IDEA
3. Clean up the server when you close the IDE

### Manual Setup

### 1. Clone the repository

```bash
git clone <repository-url>
cd mcp-plugin
```

### 2. Build the plugin

```bash
./gradlew buildPlugin
```

### 3. Run the plugin in a development IDE instance

```bash
./gradlew runIde
```

This will launch a new IntelliJ IDEA instance with the plugin installed.

### 4. Alternative: Install the plugin manually

1. Build the plugin distribution:
   ```bash
   ./gradlew buildPlugin
   ```

2. The plugin ZIP file will be created in `build/distributions/`

3. In IntelliJ IDEA/PyCharm:
   - Go to **File → Settings → Plugins**
   - Click the gear icon → **Install Plugin from Disk...**
   - Select the generated ZIP file
   - Restart the IDE

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

## Example Usage

### 🚀 Quick Development Start
1. **Terminal 1**: `./dev-start.sh server-watch` (starts server with auto-reload)
2. **Terminal 2**: `./dev-start.sh inspector` (starts IntelliJ plugin)
3. Open MCP Inspector Lite tool window in the launched IDE
4. Connect to `http://localhost:3000`
5. Try the tools:
   - **echo**: `{"text": "Hello MCP!"}`
   - **calculate**: `{"expression": "2 + 3 * 4"}`
   - **server_info**: `{}` (no parameters needed)

### 🔄 Development Workflow
1. Edit server code in `test-server/mcp_server.py`
2. Server automatically restarts (if using `--watch`)
3. Test changes immediately in the inspector
4. No need to restart IntelliJ

### 📊 Legacy Single-Command Start
1. Run: `./run-plugin-with-server.sh`
2. Open MCP Inspector Lite tool window in the launched IDE
3. Connect to `http://localhost:3000`
4. Try the "echo" tool with parameters: `{"text": "Hello MCP!"}`

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

### Building for Distribution

```bash
# Build plugin ZIP
./gradlew buildPlugin

# Verify plugin
./gradlew verifyPlugin

# Run tests
./gradlew test
```

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

## Troubleshooting

### Plugin Won't Load

1. Check that you're using IntelliJ IDEA 2023.3+ or PyCharm 2023.3+
2. Verify JDK 17+ is configured
3. Check the IDE logs for error messages

### Connection Issues

1. Ensure the MCP server is running and accessible
2. Check the server URL format (include `http://`)
3. Verify no firewall is blocking the connection
4. Check server logs for error messages

### Tool Invocation Errors

1. Verify parameter JSON syntax is correct
2. Check that required parameters are provided
3. Review tool input schema requirements
4. Check server logs for detailed error information

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is provided as an example implementation for educational purposes.