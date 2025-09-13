# MCP Inspector Lite

A minimal IntelliJ/PyCharm plugin that connects to Model Context Protocol (MCP) servers, lists available tools, and allows users to invoke tools with parameters.

## Features

- **Connect to MCP servers** - Support for HTTP-based MCP server connections
- **Browse available tools** - View all tools provided by the connected MCP server
- **Invoke tools with parameters** - Execute tools with custom parameters and view results
- **Modern UI** - Built with Jetpack Compose for a responsive and intuitive interface

## Plugin Architecture

The plugin consists of three main panes:

1. **Connection Pane** (Top) - For connecting/disconnecting to an MCP server
2. **Tools Pane** (Left) - Display a list of available tools from the server
3. **Details & Results Pane** (Right) - Show tool details, parameter input, and execution results

## Prerequisites

- IntelliJ IDEA or PyCharm (2023.3.2 or later)
- Java 17 or later
- Python 3.8+ (for running the MCP server)

## Setup Instructions

### 1. Running the MCP Server

The plugin includes a simple MCP server in the `simple-server-setup/` folder for testing.

#### Install Dependencies

First, install the required Python dependencies:

```bash
cd simple-server-setup
pip install mcp fastmcp python-dotenv
```

#### Start the Server

Run the MCP server with SSE transport:

```bash
cd simple-server-setup
python server.py
```

The server will start on `http://localhost:8050` and provide a simple calculator tool (`add`) that adds two numbers.

You should see output like:
```
Running server with SSE transport
Server running on http://0.0.0.0:8050
```

### 2. Building and Running the Plugin

#### Build the Plugin

```bash
./gradlew build
```

#### Run the Plugin in Development Mode

```bash
./gradlew runIde
```

This will start a new instance of IntelliJ IDEA with the plugin loaded.

#### Install the Plugin

To install the plugin in your regular IDE:

1. Build the plugin: `./gradlew buildPlugin`
2. The plugin ZIP will be created in `build/distributions/`
3. In IntelliJ IDEA/PyCharm, go to **Settings** → **Plugins** → **Install Plugin from Disk**
4. Select the generated ZIP file

## Using the Plugin

### 1. Open the Tool Window

Once the plugin is installed, you'll find the "MCP Inspector Lite" tool window in the right sidebar of your IDE.

### 2. Connect to the MCP Server

1. In the Connection Pane (top), enter the server URL: `http://localhost:8050/sse`
2. Click the "Connect" button
3. If successful, you'll see "Connected" status and server information

### 3. Browse Available Tools

After connecting, the Tools Pane (left) will display all available tools from the server. Click on any tool to select it.

### 4. Execute Tools

1. Select a tool from the Tools Pane
2. In the Details & Results Pane (right), enter the required parameters
3. Click "Execute Tool" to run the tool
4. View the results in the same pane

## Example Usage

With the included calculator server:

1. Connect to `http://localhost:8050/sse`
2. Select the "add" tool from the tools list
3. Enter values for parameters `a` and `b` (e.g., 5 and 3)
4. Click "Execute Tool"
5. See the result: "8"

## Development

### Project Structure

```
src/main/kotlin/com/example/mcpinspector/
├── model/
│   └── McpModels.kt          # MCP protocol data models
├── mcp/
│   └── McpClient.kt          # MCP client for server communication
└── ui/
    ├── McpToolWindowFactory.kt  # Tool window factory
    ├── McpInspectorApp.kt       # Main Compose application
    ├── ConnectionPane.kt        # Connection management UI
    ├── ToolsPane.kt            # Tools list UI
    └── DetailsPane.kt          # Tool details and execution UI
```

### Key Technologies

- **Kotlin** - Primary development language
- **Jetpack Compose** - Modern UI framework
- **Ktor Client** - HTTP client for MCP communication
- **Kotlinx Serialization** - JSON serialization for MCP protocol
- **IntelliJ Platform SDK** - Plugin development framework

### MCP Protocol Support

The plugin implements core MCP protocol features:

- **Initialize** - Establish connection with MCP server
- **tools/list** - Retrieve available tools from server
- **tools/call** - Execute tools with parameters

### Extending the Plugin

To add support for additional MCP features:

1. Update `McpModels.kt` with new protocol models
2. Add corresponding methods to `McpClient.kt`
3. Update the UI components as needed

## Troubleshooting

### Connection Issues

- Ensure the MCP server is running on the specified URL
- Check that the server is configured for HTTP transport
- Verify firewall settings allow connections to the server port

### Build Issues

- Ensure Java 17+ is installed and configured
- Run `./gradlew clean build` to clean and rebuild
- Check that all dependencies are properly resolved

### Plugin Loading Issues

- Verify the plugin is compatible with your IDE version
- Check the IDE logs for any error messages
- Try rebuilding and reinstalling the plugin

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is provided as an example implementation. Please refer to your organization's licensing requirements.

## Support

For issues and questions:

1. Check the troubleshooting section above
2. Review the MCP protocol documentation
3. Create an issue in the project repository

---

**Note**: This is a minimal implementation focused on demonstrating MCP integration. Production use may require additional error handling, security considerations, and feature enhancements.
