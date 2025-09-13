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
 * MCP Client for SSE (Server-Sent Events) transport
 * Implements proper SSE communication with MCP servers using session-based approach
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
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<McpResponse>>()
    
    private var sseJob: Job? = null
    private var currentServerUrl: String = ""
    private var sessionId: String? = null
    private var messagesEndpoint: String? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _toolsState = MutableStateFlow(ToolsState())
    val toolsState: StateFlow<ToolsState> = _toolsState.asStateFlow()
    
    private val _executionState = MutableStateFlow(ExecutionState())
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()
    
    /**
     * Connect to MCP server using SSE transport
     */
    suspend fun connect(serverUrl: String): Result<Unit> {
        return try {
            _connectionState.value = _connectionState.value.copy(
                status = "Connecting...",
                serverUrl = serverUrl
            )
            
            currentServerUrl = serverUrl
            
            // Start SSE connection and get session info
            val sessionResult = startSseConnection(serverUrl)
            if (sessionResult.isFailure) {
                _connectionState.value = _connectionState.value.copy(
                    status = "Failed to establish SSE connection: ${sessionResult.exceptionOrNull()?.message}",
                    isConnected = false
                )
                return sessionResult
            }
            
            // Initialize the MCP session
            val initResult = initialize()
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
                disconnect()
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
     * Start SSE connection and extract session information
     */
    private suspend fun startSseConnection(serverUrl: String): Result<Unit> {
        return try {
            val deferred = CompletableDeferred<Unit>()
            
            sseJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    httpClient.prepareGet(serverUrl).execute { response ->
                        if (response.status == HttpStatusCode.OK) {
                            val channel = response.bodyAsChannel()
                            
                            while (!channel.isClosedForRead) {
                                val chunk = channel.readUTF8Line(limit = 8192)
                                if (chunk != null) {
                                    when {
                                        chunk.startsWith("event: endpoint") -> {
                                            // Next line should contain the messages endpoint
                                            continue
                                        }
                                        chunk.startsWith("data: /messages/") -> {
                                            // Extract session info from endpoint
                                            val endpoint = chunk.substring(6).trim()
                                            messagesEndpoint = endpoint
                                            sessionId = endpoint.substringAfter("session_id=")
                                            
                                            // Signal that we have session info
                                            if (!deferred.isCompleted) {
                                                deferred.complete(Unit)
                                            }
                                        }
                                        chunk.startsWith("data: ") -> {
                                            val data = chunk.substring(6).trim()
                                            if (data.isNotBlank() && !data.startsWith(":") && data != "[DONE]") {
                                                try {
                                                    processMessage(data)
                                                } catch (e: Exception) {
                                                    println("Failed to process SSE message: $data, error: ${e.message}")
                                                }
                                            }
                                        }
                                        chunk.startsWith(": ping") -> {
                                            // Ignore ping messages
                                            continue
                                        }
                                        chunk.isEmpty() -> {
                                            // Empty line indicates end of message
                                            continue
                                        }
                                    }
                                } else {
                                    delay(100) // Small delay to prevent busy waiting
                                }
                            }
                        } else {
                            throw Exception("SSE connection failed: ${response.status}")
                        }
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("Cancelled") != true) {
                        if (!deferred.isCompleted) {
                            deferred.completeExceptionally(e)
                        }
                    }
                }
            }
            
            // Wait for session info or timeout
            withTimeout(10000) {
                deferred.await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from MCP server
     */
    suspend fun disconnect() {
        sseJob?.cancel()
        pendingRequests.clear()
        sessionId = null
        messagesEndpoint = null
        
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
    private suspend fun initialize(): Result<ServerInfo> {
        return try {
            val request = McpRequest(
                id = generateRequestId(),
                method = "initialize",
                params = json.encodeToJsonElement(InitializeRequest()).jsonObject
            )
            
            val response = sendRequest(request)
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
            
            val response = sendRequest(request)
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
            
            val response = sendRequest(request)
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
     * Send request to MCP server via the messages endpoint
     */
    private suspend fun sendRequest(request: McpRequest): McpResponse {
        val deferred = CompletableDeferred<McpResponse>()
        pendingRequests[request.id] = deferred
        
        return try {
            val endpoint = messagesEndpoint
            if (endpoint == null) {
                throw Exception("No messages endpoint available")
            }
            
            // Send request via HTTP POST to the messages endpoint
            val baseUrl = currentServerUrl.replace("/sse", "")
            val fullUrl = "$baseUrl$endpoint"
            
            val response = httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            
            if (response.status != HttpStatusCode.OK) {
                pendingRequests.remove(request.id)
                McpResponse(
                    id = request.id,
                    error = McpError(
                        code = response.status.value,
                        message = "HTTP ${response.status}: ${response.bodyAsText()}"
                    )
                )
            } else {
                // Wait for response via SSE
                withTimeout(30000) { // 30 second timeout
                    deferred.await()
                }
            }
        } catch (e: Exception) {
            pendingRequests.remove(request.id)
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
     * Process incoming SSE messages
     */
    private fun processMessage(message: String) {
        try {
            val response = json.decodeFromString<McpResponse>(message)
            response.id?.let { id ->
                pendingRequests.remove(id)?.complete(response)
            }
        } catch (e: Exception) {
            println("Failed to process message: $message, error: ${e.message}")
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