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
    
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
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
            println("McpClient: Starting connection to $serverUrl")
            _connectionState.value = _connectionState.value.copy(
                status = "Connecting...",
                serverUrl = serverUrl
            )
            
            currentServerUrl = serverUrl
            
            // Start SSE connection and get session info
            println("McpClient: Starting SSE connection...")
            val sessionResult = startSseConnection(serverUrl)
            if (sessionResult.isFailure) {
                val errorMsg = "Failed to establish SSE connection: ${sessionResult.exceptionOrNull()?.message}"
                println("McpClient: $errorMsg")
                _connectionState.value = _connectionState.value.copy(
                    status = errorMsg,
                    isConnected = false
                )
                return sessionResult
            }
            
            println("McpClient: SSE connection established, initializing MCP session...")
            // Initialize the MCP session
            val initResult = initialize()
            if (initResult.isSuccess) {
                println("McpClient: MCP session initialized successfully")
                _connectionState.value = _connectionState.value.copy(
                    isConnected = true,
                    status = "Connected",
                    serverInfo = initResult.getOrNull()
                )
                
                // Load available tools
                println("McpClient: Loading available tools...")
                loadTools()
                
                Result.success(Unit)
            } else {
                val errorMsg = "Connection failed: ${initResult.exceptionOrNull()?.message}"
                println("McpClient: $errorMsg")
                disconnect()
                _connectionState.value = _connectionState.value.copy(
                    status = errorMsg,
                    isConnected = false
                )
                Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize"))
            }
        } catch (e: Exception) {
            val errorMsg = "Connection failed: ${e.message}"
            println("McpClient: $errorMsg")
            println("McpClient: Exception stack trace: ${e.stackTraceToString()}")
            _connectionState.value = _connectionState.value.copy(
                status = errorMsg,
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
            println("McpClient: Attempting to connect to SSE endpoint: $serverUrl")
            val deferred = CompletableDeferred<Unit>()
            
            sseJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    println("McpClient: Making HTTP GET request to $serverUrl")
                    httpClient.prepareGet(serverUrl).execute { response ->
                        println("McpClient: Received HTTP response with status: ${response.status}")
                        if (response.status == HttpStatusCode.OK) {
                            val channel = response.bodyAsChannel()
                            println("McpClient: Starting to read SSE stream...")
                            
                            while (!channel.isClosedForRead) {
                                val chunk = channel.readUTF8Line(limit = 8192)
                                if (chunk != null) {
                                    println("McpClient: Received SSE chunk: '$chunk'")
                                    when {
                                        chunk.startsWith("event: endpoint") -> {
                                            println("McpClient: Received endpoint event")
                                            continue
                                        }
                                        chunk.startsWith("event: message") -> {
                                            println("McpClient: Received message event")
                                            continue
                                        }
                                        chunk.startsWith("data: ") -> {
                                            val data = chunk.substring(6).trim()
                                            println("McpClient: Processing data: '$data'")
                                            
                                            when {
                                                data.startsWith("/messages/") || data.startsWith("/messages?") -> {
                                                    // Extract session info from endpoint
                                                    println("McpClient: Found messages endpoint: $data")
                                                    messagesEndpoint = data
                                                    
                                                    // Extract session ID from the endpoint
                                                    val sessionIdMatch = Regex("session_id=([^&]+)").find(data)
                                                    if (sessionIdMatch != null) {
                                                        sessionId = sessionIdMatch.groupValues[1]
                                                        println("McpClient: Extracted session ID: $sessionId")
                                                    } else {
                                                        println("McpClient: No session ID found in endpoint")
                                                    }
                                                    
                                                    // Signal that we have session info
                                                    if (!deferred.isCompleted) {
                                                        println("McpClient: Session info obtained, completing connection setup")
                                                        deferred.complete(Unit)
                                                    }
                                                }
                                                data.startsWith("{") -> {
                                                    // JSON response
                                                    try {
                                                        println("McpClient: Processing JSON message: $data")
                                                        processMessage(data)
                                                    } catch (e: Exception) {
                                                        println("McpClient: Failed to process JSON message: $data, error: ${e.message}")
                                                    }
                                                }
                                                data.isBlank() || data.startsWith(":") || data == "[DONE]" -> {
                                                    // Ignore empty data, comments, or done markers
                                                    continue
                                                }
                                                else -> {
                                                    println("McpClient: Unhandled data format: $data")
                                                }
                                            }
                                        }
                                        chunk.startsWith(":") -> {
                                            // Comment line (including ping)
                                            println("McpClient: Received comment: $chunk")
                                            continue
                                        }
                                        chunk.isEmpty() -> {
                                            // Empty line indicates end of message
                                            continue
                                        }
                                        else -> {
                                            println("McpClient: Unhandled SSE chunk: '$chunk'")
                                        }
                                    }
                                } else {
                                    delay(100) // Small delay to prevent busy waiting
                                }
                            }
                            println("McpClient: SSE stream closed")
                        } else {
                            throw Exception("SSE connection failed: ${response.status} - ${response.bodyAsText()}")
                        }
                    }
                } catch (e: Exception) {
                    println("McpClient: SSE connection error: ${e.message}")
                    if (e.message?.contains("Cancelled") != true) {
                        if (!deferred.isCompleted) {
                            deferred.completeExceptionally(e)
                        }
                    }
                }
            }
            
            // Wait for session info or timeout
            println("McpClient: Waiting for session info (30s timeout)...")
            withTimeout(30000) { // Increased timeout to 30 seconds
                deferred.await()
            }
            
            println("McpClient: SSE connection setup completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("McpClient: SSE connection setup failed: ${e.message}")
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
            println("McpClient: Initializing MCP session...")
            val initRequest = InitializeRequest()
            val request = McpRequest(
                jsonrpc = "2.0",
                id = generateRequestId(),
                method = "initialize",
                params = json.encodeToJsonElement(initRequest).jsonObject
            )
            
            println("McpClient: Sending initialize request with ID: ${request.id}")
            println("McpClient: Messages endpoint: $messagesEndpoint")
            
            val response = sendRequest(request)
            if (response.error != null) {
                println("McpClient: Initialize failed with error: ${response.error.message}")
                Result.failure(Exception("Initialize failed: ${response.error.message}"))
            } else {
                println("McpClient: Initialize successful, parsing server info...")
                val result = json.decodeFromJsonElement<InitializeResult>(response.result!!)
                println("McpClient: Server info: ${result.serverInfo.name} v${result.serverInfo.version}")
                
                // Send initialized notification as required by MCP protocol
                println("McpClient: Sending initialized notification...")
                sendInitializedNotification()
                
                // Small delay to ensure server processes the initialized notification
                delay(100)
                
                Result.success(result.serverInfo)
            }
        } catch (e: Exception) {
            println("McpClient: Initialize exception: ${e.message}")
            println("McpClient: Initialize exception stack trace: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }
    
    /**
     * Send initialized notification to complete MCP handshake
     */
    private suspend fun sendInitializedNotification() {
        try {
            val notification = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "notifications/initialized")
            }
            
            val endpoint = messagesEndpoint
            if (endpoint != null) {
                val baseUrl = currentServerUrl.replace("/sse", "")
                val fullUrl = "$baseUrl$endpoint"
                
                println("McpClient: Sending initialized notification to $fullUrl")
                
                val response = httpClient.post(fullUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(notification))
                }
                
                println("McpClient: Initialized notification response: ${response.status}")
            }
        } catch (e: Exception) {
            println("McpClient: Failed to send initialized notification: ${e.message}")
        }
    }
    
    /**
     * Load available tools from server
     */
    suspend fun loadTools(): Result<List<Tool>> {
        return try {
            println("McpClient: Loading tools from server...")
            _toolsState.value = _toolsState.value.copy(isLoading = true, error = null)
            
            val request = McpRequest(
                jsonrpc = "2.0",
                id = generateRequestId(),
                method = "tools/list"
            )
            
            val response = sendRequest(request)
            if (response.error != null) {
                val error = "Failed to load tools: ${response.error.message}"
                println("McpClient: $error")
                _toolsState.value = _toolsState.value.copy(
                    isLoading = false,
                    error = error
                )
                Result.failure(Exception(error))
            } else {
                val result = json.decodeFromJsonElement<ListToolsResult>(response.result!!)
                println("McpClient: Successfully loaded ${result.tools.size} tools")
                _toolsState.value = _toolsState.value.copy(
                    tools = result.tools,
                    isLoading = false,
                    error = null
                )
                Result.success(result.tools)
            }
        } catch (e: Exception) {
            val error = "Failed to load tools: ${e.message}"
            println("McpClient: $error")
            println("McpClient: Load tools exception: ${e.stackTraceToString()}")
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
            
            val callRequest = CallToolRequest(toolName, arguments)
            val request = McpRequest(
                jsonrpc = "2.0",
                id = generateRequestId(),
                method = "tools/call",
                params = json.encodeToJsonElement(callRequest).jsonObject
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
                println("McpClient: No messages endpoint available for request ${request.id}")
                throw Exception("No messages endpoint available")
            }
            
            // Send request via HTTP POST to the messages endpoint
            val baseUrl = currentServerUrl.replace("/sse", "")
            val fullUrl = "$baseUrl$endpoint"
            
            println("McpClient: Sending ${request.method} request to $fullUrl")
            println("McpClient: Request body: ${json.encodeToString(request)}")
            
            val response = httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            
            println("McpClient: HTTP POST response status: ${response.status}")
            
            // Accept both 200 OK and 202 Accepted as successful responses
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Accepted) {
                val responseBody = response.bodyAsText()
                println("McpClient: HTTP POST failed: ${response.status} - $responseBody")
                pendingRequests.remove(request.id)
                McpResponse(
                    id = request.id,
                    error = McpError(
                        code = response.status.value,
                        message = "HTTP ${response.status}: $responseBody"
                    )
                )
            } else {
                println("McpClient: HTTP POST successful (${response.status}), waiting for SSE response...")
                // Wait for response via SSE
                withTimeout(30000) { // 30 second timeout
                    deferred.await()
                }
            }
        } catch (e: Exception) {
            println("McpClient: Request ${request.id} failed: ${e.message}")
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
            println("Processing SSE message: $message")
            val response = json.decodeFromString<McpResponse>(message)
            println("Parsed response: id=${response.id}, error=${response.error}, result=${response.result}")
            response.id?.let { id ->
                val deferred = pendingRequests.remove(id)
                if (deferred != null) {
                    println("Completing request $id")
                    deferred.complete(response)
                } else {
                    println("No pending request found for id: $id")
                }
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