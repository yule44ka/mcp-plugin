package com.example.mcpinspector.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * MCP Protocol Models
 * Based on the Model Context Protocol specification
 */

@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: JsonElement? = null,
    val error: McpError? = null
)

@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class InitializeRequest(
    val protocolVersion: String = "2024-11-05",
    val capabilities: ClientCapabilities = ClientCapabilities(),
    val clientInfo: ClientInfo = ClientInfo()
)

@Serializable
data class ClientCapabilities(
    val experimental: JsonObject? = null,
    val sampling: JsonObject? = null
)

@Serializable
data class ClientInfo(
    val name: String = "MCP Inspector Lite",
    val version: String = "1.0.0"
)

@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo
)

@Serializable
data class ServerCapabilities(
    val logging: JsonObject? = null,
    val prompts: JsonObject? = null,
    val resources: JsonObject? = null,
    val tools: JsonObject? = null
)

@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

@Serializable
data class ListToolsResult(
    val tools: List<Tool>
)

@Serializable
data class Tool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject
)

@Serializable
data class CallToolRequest(
    val name: String,
    val arguments: JsonObject? = null
)

@Serializable
data class CallToolResult(
    val content: List<ToolContent>,
    val isError: Boolean? = null
)

@Serializable
data class ToolContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

// UI State Models
data class ConnectionState(
    val isConnected: Boolean = false,
    val serverUrl: String = "http://localhost:8050/sse",
    val status: String = "Disconnected",
    val serverInfo: ServerInfo? = null
)

data class ToolsState(
    val tools: List<Tool> = emptyList(),
    val selectedTool: Tool? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ExecutionState(
    val isExecuting: Boolean = false,
    val result: CallToolResult? = null,
    val error: String? = null,
    val parameters: Map<String, String> = emptyMap()
)
