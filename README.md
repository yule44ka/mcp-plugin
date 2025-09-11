# MCP Inspector Lite

A minimal IntelliJ/PyCharm plugin in Kotlin that connects to Model Context Protocol (MCP) servers, lists available tools, and allows users to invoke tools with parameters.

## Features

- **Connection Management**: Connect to and disconnect from MCP servers
- **Tool Discovery**: Browse available tools from connected MCP servers
- **Tool Invocation**: Execute tools with custom parameters and view results
- **Modern UI**: Built with Compose Multiplatform for a responsive interface
- **Three-Pane Layout**: 
  - Connection Pane: Server connection management
  - Tools Pane: Available tools listing
  - Details & Results Pane: Tool details, parameter input, and execution results

## Architecture

The plugin is structured with the following components:

- **MCP Client** (`McpClient.kt`): Handles JSON-RPC communication with MCP servers
- **UI Components** (`McpInspectorApp.kt`): Compose-based user interface
- **Tool Window Factory** (`McpToolWindowFactory.kt`): IntelliJ plugin integration
- **Data Models** (`McpModels.kt`): MCP protocol data structures

## Prerequisites

- IntelliJ IDEA 2023.3+ or PyCharm 2023.3+
- JDK 17 or higher
- Python 3.7+ (for running the test server)

## Running the MCP Test Server

The project includes a simple Python-based MCP server for testing purposes.

### 1. Navigate to the test server directory

```bash
cd test-server
```

### 2. Run the server

```bash
python3 mcp_server.py --port 3000 --host localhost
```

The server will start on `http://localhost:3000` and provide the following test tools:

- **echo**: Echoes back input text
- **add_numbers**: Adds two numbers together
- **get_time**: Returns current server time
- **reverse_string**: Reverses a string

### 3. Server Options

```bash
python3 mcp_server.py --help
```

Options:
- `--port`: Port to run the server on (default: 3000)
- `--host`: Host to bind the server to (default: localhost)

## Building and Running the Plugin

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
3. Enter parameters in JSON format (e.g., `{"text": "Hello World"}`)
4. Click **Invoke Tool** to execute the tool
5. View the results in the same pane

## Example Usage

1. Start the test server: `python3 test-server/mcp_server.py`
2. Run the plugin: `./gradlew runIde`
3. Open MCP Inspector Lite tool window
4. Connect to `http://localhost:3000`
5. Try the "echo" tool with parameters: `{"text": "Hello MCP!"}`

## Development

### Project Structure

```
mcp-plugin/
├── build.gradle.kts              # Gradle build configuration
├── src/main/
│   ├── kotlin/com/example/mcpinspector/
│   │   ├── mcp/
│   │   │   └── McpClient.kt      # MCP protocol client
│   │   ├── model/
│   │   │   └── McpModels.kt      # Data models
│   │   └── ui/
│   │       ├── McpInspectorApp.kt        # Main Compose UI
│   │       └── McpToolWindowFactory.kt   # Plugin integration
│   └── resources/
│       ├── META-INF/plugin.xml   # Plugin configuration
│       └── icons/mcp-icon.svg    # Plugin icon
├── test-server/
│   ├── mcp_server.py            # Test MCP server
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

## AI Assistant Usage

This project was developed with assistance from AI coding assistants. All interactions and decisions made during development have been documented to demonstrate the collaborative workflow between human developers and AI tools.

The AI assistants helped with:
- Project structure and best practices
- Kotlin and Compose implementation
- MCP protocol understanding
- Error handling and debugging
- Documentation and README creation