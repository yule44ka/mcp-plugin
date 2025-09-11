package com.example.mcpinspector.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 request structure
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonElement? = null
)

/**
 * JSON-RPC 2.0 response structure
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 error structure
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * MCP Tool definition
 */
@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonElement? = null
)

/**
 * MCP Tools list response
 */
@Serializable
data class McpToolsResponse(
    val tools: List<McpTool>
)

/**
 * MCP Tool call request
 */
@Serializable
data class McpToolCallRequest(
    val name: String,
    val arguments: JsonElement? = null
)

/**
 * MCP Tool call response
 */
@Serializable
data class McpToolCallResponse(
    val content: List<McpContent>? = null,
    val isError: Boolean? = null
)

/**
 * MCP Content item
 */
@Serializable
data class McpContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

/**
 * Connection state for the MCP client
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * MCP Server configuration
 */
data class McpServerConfig(
    val name: String,
    val url: String,
    val type: ServerType = ServerType.HTTP
)

enum class ServerType {
    HTTP,
    WEBSOCKET,
    STDIO
}
