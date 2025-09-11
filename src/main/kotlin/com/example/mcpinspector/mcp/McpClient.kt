package com.example.mcpinspector.mcp

import com.example.mcpinspector.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * MCP Client for communicating with Model Context Protocol servers
 */
class McpClient {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    private val requestIdCounter = AtomicLong(0)
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _tools = MutableStateFlow<List<McpTool>>(emptyList())
    val tools: StateFlow<List<McpTool>> = _tools.asStateFlow()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    private var currentServerConfig: McpServerConfig? = null
    private var connectionJob: Job? = null
    
    /**
     * Connect to an MCP server
     */
    suspend fun connect(serverConfig: McpServerConfig): Result<Unit> {
        return try {
            _connectionState.value = ConnectionState.CONNECTING
            _lastError.value = null
            currentServerConfig = serverConfig
            
            // Test connection by calling tools/list
            val tools = listTools()
            if (tools.isSuccess) {
                _connectionState.value = ConnectionState.CONNECTED
                _tools.value = tools.getOrNull() ?: emptyList()
                Result.success(Unit)
            } else {
                _connectionState.value = ConnectionState.ERROR
                _lastError.value = tools.exceptionOrNull()?.message ?: "Failed to connect"
                Result.failure(tools.exceptionOrNull() ?: Exception("Connection failed"))
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            _lastError.value = e.message ?: "Unknown error"
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from the MCP server
     */
    fun disconnect() {
        connectionJob?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
        _tools.value = emptyList()
        _lastError.value = null
        currentServerConfig = null
    }
    
    /**
     * List available tools from the MCP server
     */
    suspend fun listTools(): Result<List<McpTool>> {
        val serverConfig = currentServerConfig ?: return Result.failure(Exception("Not connected"))
        
        return try {
            val request = JsonRpcRequest(
                id = generateRequestId(),
                method = "tools/list"
            )
            
            val response: JsonRpcResponse = httpClient.post(serverConfig.url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            
            if (response.error != null) {
                Result.failure(Exception("Server error: ${response.error.message}"))
            } else {
                val toolsResponse = Json.decodeFromJsonElement<McpToolsResponse>(
                    response.result ?: JsonObject(emptyMap())
                )
                Result.success(toolsResponse.tools)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Call a specific tool with parameters
     */
    suspend fun callTool(toolName: String, arguments: JsonElement?): Result<McpToolCallResponse> {
        val serverConfig = currentServerConfig ?: return Result.failure(Exception("Not connected"))
        
        return try {
            val toolCallRequest = McpToolCallRequest(
                name = toolName,
                arguments = arguments
            )
            
            val request = JsonRpcRequest(
                id = generateRequestId(),
                method = "tools/call",
                params = Json.encodeToJsonElement(toolCallRequest)
            )
            
            val response: JsonRpcResponse = httpClient.post(serverConfig.url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            
            if (response.error != null) {
                Result.failure(Exception("Server error: ${response.error.message}"))
            } else {
                val toolCallResponse = Json.decodeFromJsonElement<McpToolCallResponse>(
                    response.result ?: JsonObject(emptyMap())
                )
                Result.success(toolCallResponse)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get server capabilities (initialize handshake)
     */
    suspend fun initialize(): Result<JsonElement> {
        val serverConfig = currentServerConfig ?: return Result.failure(Exception("Not connected"))
        
        return try {
            val request = JsonRpcRequest(
                id = generateRequestId(),
                method = "initialize",
                params = buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", buildJsonObject {
                        put("tools", buildJsonObject {
                            put("listChanged", true)
                        })
                    })
                    put("clientInfo", buildJsonObject {
                        put("name", "MCP Inspector Lite")
                        put("version", "1.0.0")
                    })
                }
            )
            
            val response: JsonRpcResponse = httpClient.post(serverConfig.url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            
            if (response.error != null) {
                Result.failure(Exception("Server error: ${response.error.message}"))
            } else {
                Result.success(response.result ?: JsonObject(emptyMap()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun generateRequestId(): String {
        return "req_${requestIdCounter.incrementAndGet()}_${UUID.randomUUID().toString().take(8)}"
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        disconnect()
        httpClient.close()
    }
}
