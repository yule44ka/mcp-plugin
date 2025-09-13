package com.example.mcpinspector.mcp

import com.example.mcpinspector.model.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP Client for HTTP-based communication
 * Simplified implementation for connecting to MCP servers
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
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val requestIdCounter = AtomicInteger(0)
    
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _toolsState = MutableStateFlow(ToolsState())
    val toolsState: StateFlow<ToolsState> = _toolsState.asStateFlow()
    
    private val _executionState = MutableStateFlow(ExecutionState())
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()
    
    /**
     * Connect to MCP server using HTTP
     */
    suspend fun connect(serverUrl: String): Result<Unit> {
        return try {
            _connectionState.value = _connectionState.value.copy(
                status = "Connecting...",
                serverUrl = serverUrl
            )
            
            // Test connection by trying to initialize
            val initResult = initialize(serverUrl)
            if (initResult.isSuccess) {
                _connectionState.value = _connectionState.value.copy(
                    isConnected = true,
                    status = "Connected",
                    serverInfo = initResult.getOrNull()
                )
                
                // Load available tools
                loadTools()
                
                Result.success(Unit)
            } else {
                _connectionState.value = _connectionState.value.copy(
                    status = "Connection failed: ${initResult.exceptionOrNull()?.message}",
                    isConnected = false
                )
                Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize"))
            }
        } catch (e: Exception) {
            _connectionState.value = _connectionState.value.copy(
                status = "Connection failed: ${e.message}",
                isConnected = false
            )
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from MCP server
     */
    suspend fun disconnect() {
        _connectionState.value = ConnectionState(
            isConnected = false,
            status = "Disconnected",
            serverUrl = _connectionState.value.serverUrl
        )
        _toolsState.value = ToolsState()
        _executionState.value = ExecutionState()
    }
    
    /**
     * Initialize MCP session
     */
    private suspend fun initialize(serverUrl: String): Result<ServerInfo> {
        return try {
            val request = McpRequest(
                id = generateRequestId(),
                method = "initialize",
                params = json.encodeToJsonElement(InitializeRequest()).jsonObject
            )
            
            val response = sendHttpRequest(serverUrl, request)
            if (response.error != null) {
                Result.failure(Exception("Initialize failed: ${response.error.message}"))
            } else {
                val result = json.decodeFromJsonElement<InitializeResult>(response.result!!)
                Result.success(result.serverInfo)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Load available tools from server
     */
    suspend fun loadTools(): Result<List<Tool>> {
        return try {
            _toolsState.value = _toolsState.value.copy(isLoading = true, error = null)
            
            val request = McpRequest(
                id = generateRequestId(),
                method = "tools/list"
            )
            
            val response = sendHttpRequest(_connectionState.value.serverUrl, request)
            if (response.error != null) {
                val error = "Failed to load tools: ${response.error.message}"
                _toolsState.value = _toolsState.value.copy(
                    isLoading = false,
                    error = error
                )
                Result.failure(Exception(error))
            } else {
                val result = json.decodeFromJsonElement<ListToolsResult>(response.result!!)
                _toolsState.value = _toolsState.value.copy(
                    tools = result.tools,
                    isLoading = false,
                    error = null
                )
                Result.success(result.tools)
            }
        } catch (e: Exception) {
            val error = "Failed to load tools: ${e.message}"
            _toolsState.value = _toolsState.value.copy(
                isLoading = false,
                error = error
            )
            Result.failure(e)
        }
    }
    
    /**
     * Call a tool with parameters
     */
    suspend fun callTool(toolName: String, parameters: Map<String, Any>): Result<CallToolResult> {
        return try {
            _executionState.value = _executionState.value.copy(
                isExecuting = true,
                error = null,
                result = null
            )
            
            val arguments = buildJsonObject {
                parameters.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Int -> put(key, value)
                        is Double -> put(key, value)
                        is Boolean -> put(key, value)
                        else -> put(key, value.toString())
                    }
                }
            }
            
            val request = McpRequest(
                id = generateRequestId(),
                method = "tools/call",
                params = json.encodeToJsonElement(CallToolRequest(toolName, arguments)).jsonObject
            )
            
            val response = sendHttpRequest(_connectionState.value.serverUrl, request)
            if (response.error != null) {
                val error = "Tool execution failed: ${response.error.message}"
                _executionState.value = _executionState.value.copy(
                    isExecuting = false,
                    error = error
                )
                Result.failure(Exception(error))
            } else {
                val result = json.decodeFromJsonElement<CallToolResult>(response.result!!)
                _executionState.value = _executionState.value.copy(
                    isExecuting = false,
                    result = result
                )
                Result.success(result)
            }
        } catch (e: Exception) {
            val error = "Tool execution failed: ${e.message}"
            _executionState.value = _executionState.value.copy(
                isExecuting = false,
                error = error
            )
            Result.failure(e)
        }
    }
    
    /**
     * Send HTTP request to MCP server
     */
    private suspend fun sendHttpRequest(serverUrl: String, request: McpRequest): McpResponse {
        return try {
            // For now, we'll use a simple HTTP POST approach
            // In a real implementation, this would need to handle the specific MCP transport
            val baseUrl = serverUrl.replace("/sse", "")
            val response = httpClient.post("$baseUrl/mcp") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            
            if (response.status == HttpStatusCode.OK) {
                val responseText = response.bodyAsText()
                json.decodeFromString<McpResponse>(responseText)
            } else {
                McpResponse(
                    id = request.id,
                    error = McpError(
                        code = response.status.value,
                        message = "HTTP ${response.status}: ${response.bodyAsText()}"
                    )
                )
            }
        } catch (e: Exception) {
            McpResponse(
                id = request.id,
                error = McpError(
                    code = -1,
                    message = "Request failed: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Generate unique request ID
     */
    private fun generateRequestId(): String {
        return "req_${requestIdCounter.incrementAndGet()}_${UUID.randomUUID().toString().take(8)}"
    }
    
    /**
     * Select a tool for detailed view
     */
    fun selectTool(tool: Tool?) {
        _toolsState.value = _toolsState.value.copy(selectedTool = tool)
        _executionState.value = ExecutionState() // Reset execution state
    }
    
    /**
     * Update execution parameters
     */
    fun updateParameters(parameters: Map<String, String>) {
        _executionState.value = _executionState.value.copy(parameters = parameters)
    }
    
    /**
     * Clean up resources
     */
    fun dispose() {
        runBlocking {
            disconnect()
        }
        httpClient.close()
    }
}