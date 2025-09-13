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
        
        // Configure CIO engine for long-lived connections
        engine {
            endpoint {
                keepAliveTime = 30_000 // Keep connection alive for 30 seconds
                pipelining = false
                connectTimeout = 30_000 // 30 seconds to establish connection
                requestTimeout = 0 // Disable request timeout for SSE
            }
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
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private var isReconnecting = false
    
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _toolsState = MutableStateFlow(ToolsState())
    val toolsState: StateFlow<ToolsState> = _toolsState.asStateFlow()
    
    private val _executionState = MutableStateFlow(ExecutionState())
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()
    
    private val _historyState = MutableStateFlow(HistoryState())
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()
    
    private val _notificationsState = MutableStateFlow(NotificationsState())
    val notificationsState: StateFlow<NotificationsState> = _notificationsState.asStateFlow()
    
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
            
            // Add notification about connection start
            addNotification(
                type = NotificationType.INFO,
                title = "Connecting to Server",
                message = "Starting connection to $serverUrl"
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
                
                // Add notification about SSE connection failure
                addNotification(
                    type = NotificationType.ERROR,
                    title = "Connection Failed",
                    message = "Failed to establish SSE connection: ${sessionResult.exceptionOrNull()?.message}"
                )
                
                return sessionResult
            }
            
            println("McpClient: SSE connection established, initializing MCP session...")
            // Initialize the MCP session
            val initResult = initialize()
            if (initResult.isSuccess) {
                println("McpClient: MCP session initialized successfully")
                val serverInfo = initResult.getOrNull()
                _connectionState.value = _connectionState.value.copy(
                    isConnected = true,
                    status = "Connected",
                    serverInfo = serverInfo
                )
                
                // Add notification about successful connection
                addNotification(
                    type = NotificationType.SUCCESS,
                    title = "Connected to Server",
                    message = "Successfully connected to ${serverInfo?.name ?: serverUrl}"
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
                
                // Add notification about connection failure
                addNotification(
                    type = NotificationType.ERROR,
                    title = "Connection Failed",
                    message = "Failed to initialize MCP session: ${initResult.exceptionOrNull()?.message}"
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
            
            // Add notification about connection error
            addNotification(
                type = NotificationType.ERROR,
                title = "Connection Error",
                message = "Unable to connect to server: ${e.message}"
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
                var connectionActive = true
                while (connectionActive && isActive) {
                    try {
                        println("McpClient: Making HTTP GET request to $serverUrl")
                        httpClient.prepareGet(serverUrl).execute { response ->
                            println("McpClient: Received HTTP response with status: ${response.status}")
                            if (response.status == HttpStatusCode.OK) {
                                val channel = response.bodyAsChannel()
                                println("McpClient: Starting to read SSE stream...")
                                reconnectAttempts = 0 // Reset on successful connection
                                
                                while (!channel.isClosedForRead && isActive) {
                                    try {
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
                                                    println("McpClient: Received ping: $chunk")
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
                                            // No data received, small delay
                                            delay(100)
                                        }
                                    } catch (e: Exception) {
                                        println("McpClient: Error reading SSE chunk: ${e.message}")
                                        break // Break inner loop to trigger reconnection
                                    }
                                }
                                println("McpClient: SSE stream closed")
                            } else {
                                throw Exception("SSE connection failed: ${response.status} - ${response.bodyAsText()}")
                            }
                        }
                    } catch (e: Exception) {
                        println("McpClient: SSE connection error: ${e.message}")
                        
                        if (e.message?.contains("Cancelled") == true || !isActive) {
                            connectionActive = false
                            break
                        }
                        
                        // Handle reconnection
                        if (_connectionState.value.isConnected && reconnectAttempts < maxReconnectAttempts) {
                            reconnectAttempts++
                            println("McpClient: Attempting to reconnect (attempt $reconnectAttempts/$maxReconnectAttempts)")
                            
                            addNotification(
                                type = NotificationType.WARNING,
                                title = "Connection Lost",
                                message = "Reconnecting to server (attempt $reconnectAttempts/$maxReconnectAttempts)..."
                            )
                            
                            delay(2000L * reconnectAttempts) // Exponential backoff
                        } else {
                            connectionActive = false
                            if (!deferred.isCompleted) {
                                deferred.completeExceptionally(e)
                            }
                            
                            // Update connection state to disconnected
                            _connectionState.value = _connectionState.value.copy(
                                isConnected = false,
                                status = "Connection lost: ${e.message}"
                            )
                            
                            addNotification(
                                type = NotificationType.ERROR,
                                title = "Connection Failed",
                                message = "Unable to maintain connection to server: ${e.message}"
                            )
                        }
                    }
                }
            }
            
            // Wait for session info or timeout
            println("McpClient: Waiting for session info (30s timeout)...")
            withTimeout(30000) {
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
        reconnectAttempts = 0
        isReconnecting = false
        
        _connectionState.value = ConnectionState(
            isConnected = false,
            status = "Disconnected",
            serverUrl = _connectionState.value.serverUrl
        )
        _toolsState.value = ToolsState()
        _executionState.value = ExecutionState()
        
        // Add notification about disconnection
        addNotification(
            type = NotificationType.INFO,
            title = "Disconnected",
            message = "Successfully disconnected from MCP server"
        )
    }
    
    /**
     * Restart connection to current server
     */
    suspend fun restart(): Result<Unit> {
        val currentUrl = _connectionState.value.serverUrl
        addNotification(
            type = NotificationType.INFO,
            title = "Restarting Connection",
            message = "Restarting connection to $currentUrl"
        )
        
        disconnect()
        delay(1000) // Brief delay before reconnecting
        return connect(currentUrl)
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
            
            // Check if we're connected first
            if (!_connectionState.value.isConnected) {
                val error = "Not connected to server. Please connect first."
                println("McpClient: $error")
                _toolsState.value = _toolsState.value.copy(
                    isLoading = false,
                    error = error
                )
                return Result.failure(Exception(error))
            }
            
            // Check if SSE connection is still active
            if (sseJob?.isActive != true) {
                val error = "SSE connection is not active. Please reconnect."
                println("McpClient: $error")
                _toolsState.value = _toolsState.value.copy(
                    isLoading = false,
                    error = error
                )
                return Result.failure(Exception(error))
            }
            
            _toolsState.value = _toolsState.value.copy(isLoading = true, error = null)
            
            val request = McpRequest(
                jsonrpc = "2.0",
                id = generateRequestId(),
                method = "tools/list"
            )
            
            println("McpClient: Sending tools/list request with ID: ${request.id}")
            println("McpClient: Current pending requests: ${pendingRequests.keys}")
            
            val response = sendRequest(request)
            if (response.error != null) {
                val error = "Failed to load tools: ${response.error.message}"
                println("McpClient: $error")
                _toolsState.value = _toolsState.value.copy(
                    isLoading = false,
                    error = error
                )
                
                // Add notification about the error
                addNotification(
                    type = NotificationType.ERROR,
                    title = "Tools Loading Failed",
                    message = "Failed to load tools: ${response.error.message}",
                    category = NotificationCategory.TOOL
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
                
                // Add success notification
                addNotification(
                    type = NotificationType.SUCCESS,
                    title = "Tools Loaded",
                    message = "Successfully loaded ${result.tools.size} tools",
                    category = NotificationCategory.TOOL
                )
                
                Result.success(result.tools)
            }
        } catch (e: Exception) {
            val error = when {
                e.message?.contains("timeout") == true -> "Request timed out. Server may be unresponsive or disconnected."
                e.message?.contains("Cancelled") == true -> "Request was cancelled."
                e.message?.contains("Connection") == true -> "Connection error. Please check server status."
                else -> "Failed to load tools: ${e.message}"
            }
            
            println("McpClient: $error")
            println("McpClient: Load tools exception: ${e.stackTraceToString()}")
            _toolsState.value = _toolsState.value.copy(
                isLoading = false,
                error = error
            )
            
            // Add notification about the error
            addNotification(
                type = NotificationType.ERROR,
                title = "Tools Loading Error",
                message = error,
                category = NotificationCategory.TOOL
            )
            
            Result.failure(e)
        }
    }
    
    /**
     * Call a tool with parameters
     */
    suspend fun callTool(toolName: String, parameters: Map<String, Any>): Result<CallToolResult> {
        val startTime = System.currentTimeMillis()
        val historyId = generateRequestId()
        
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
            val executionTime = System.currentTimeMillis() - startTime
            
            if (response.error != null) {
                val error = "Tool execution failed: ${response.error.message}"
                _executionState.value = _executionState.value.copy(
                    isExecuting = false,
                    error = error
                )
                
                // Add to history
                addToHistory(
                    ExecutionHistoryEntry(
                        id = historyId,
                        timestamp = System.currentTimeMillis(),
                        toolName = toolName,
                        parameters = parameters,
                        result = null,
                        error = error,
                        executionTimeMs = executionTime
                    )
                )
                
                // Add notification
                addNotification(
                    type = NotificationType.ERROR,
                    title = "Tool Execution Failed",
                    message = "Failed to execute $toolName: ${response.error.message}",
                    category = NotificationCategory.TOOL
                )
                
                Result.failure(Exception(error))
            } else {
                val result = json.decodeFromJsonElement<CallToolResult>(response.result!!)
                _executionState.value = _executionState.value.copy(
                    isExecuting = false,
                    result = result
                )
                
                // Add to history
                addToHistory(
                    ExecutionHistoryEntry(
                        id = historyId,
                        timestamp = System.currentTimeMillis(),
                        toolName = toolName,
                        parameters = parameters,
                        result = result,
                        error = null,
                        executionTimeMs = executionTime
                    )
                )
                
                // Add notification
                addNotification(
                    type = NotificationType.SUCCESS,
                    title = "Tool Executed Successfully",
                    message = "Successfully executed $toolName in ${executionTime}ms",
                    category = NotificationCategory.TOOL
                )
                
                Result.success(result)
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            val error = "Tool execution failed: ${e.message}"
            _executionState.value = _executionState.value.copy(
                isExecuting = false,
                error = error
            )
            
            // Add to history
            addToHistory(
                ExecutionHistoryEntry(
                    id = historyId,
                    timestamp = System.currentTimeMillis(),
                    toolName = toolName,
                    parameters = parameters,
                    result = null,
                    error = error,
                    executionTimeMs = executionTime
                )
            )
            
            // Add notification
            addNotification(
                type = NotificationType.ERROR,
                title = "Tool Execution Error",
                message = "Error executing $toolName: ${e.message}",
                category = NotificationCategory.TOOL
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
            
            // Check SSE connection status before sending
            if (sseJob?.isActive != true) {
                println("McpClient: SSE connection is not active for request ${request.id}")
                throw Exception("SSE connection is not active")
            }
            
            // Send request via HTTP POST to the messages endpoint
            val baseUrl = currentServerUrl.replace("/sse", "")
            val fullUrl = "$baseUrl$endpoint"
            
            println("McpClient: Sending ${request.method} request to $fullUrl")
            println("McpClient: Request body: ${json.encodeToString(request)}")
            println("McpClient: Pending requests before send: ${pendingRequests.keys}")
            
            val startTime = System.currentTimeMillis()
            val response = httpClient.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            
            val httpTime = System.currentTimeMillis() - startTime
            println("McpClient: HTTP POST response status: ${response.status} (took ${httpTime}ms)")
            
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
                println("McpClient: Pending requests after HTTP POST: ${pendingRequests.keys}")
                
                // Wait for response via SSE with shorter timeout and better error handling
                try {
                    withTimeout(15000) { // Reduced timeout to 15 seconds
                        val sseStartTime = System.currentTimeMillis()
                        val result = deferred.await()
                        val sseTime = System.currentTimeMillis() - sseStartTime
                        println("McpClient: SSE response received for ${request.id} (took ${sseTime}ms)")
                        result
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    println("McpClient: Timeout waiting for SSE response for request ${request.id}")
                    println("McpClient: SSE job active: ${sseJob?.isActive}")
                    println("McpClient: Current pending requests: ${pendingRequests.keys}")
                    pendingRequests.remove(request.id)
                    
                    // Check if SSE connection is still alive
                    if (sseJob?.isActive != true) {
                        throw Exception("SSE connection lost during request. Please reconnect.")
                    } else {
                        throw Exception("Request timed out after 15 seconds. Server may be unresponsive.")
                    }
                }
            }
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("timeout") == true || e is kotlinx.coroutines.TimeoutCancellationException -> 
                    "Request timed out. Server may be unresponsive."
                e.message?.contains("Connection") == true -> 
                    "Connection error: ${e.message}"
                e.message?.contains("SSE connection") == true -> 
                    e.message ?: "SSE connection error"
                else -> 
                    "Request failed: ${e.message}"
            }
            
            println("McpClient: Request ${request.id} failed: $errorMessage")
            println("McpClient: Exception type: ${e::class.simpleName}")
            pendingRequests.remove(request.id)
            
            McpResponse(
                id = request.id,
                error = McpError(
                    code = -1,
                    message = errorMessage
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
     * Toggle parameter input mode between FORM and JSON
     */
    fun toggleInputMode() {
        val currentMode = _executionState.value.inputMode
        val newMode = if (currentMode == ParameterInputMode.FORM) {
            ParameterInputMode.JSON
        } else {
            ParameterInputMode.FORM
        }
        _executionState.value = _executionState.value.copy(inputMode = newMode)
    }
    
    /**
     * Set parameter input mode
     */
    fun setInputMode(mode: ParameterInputMode) {
        _executionState.value = _executionState.value.copy(inputMode = mode)
    }
    
    /**
     * Add entry to execution history
     */
    private fun addToHistory(entry: ExecutionHistoryEntry) {
        val currentEntries = _historyState.value.entries.toMutableList()
        currentEntries.add(0, entry) // Add to beginning for newest first
        
        // Keep only last 100 entries
        if (currentEntries.size > 100) {
            currentEntries.removeAt(currentEntries.size - 1)
        }
        
        _historyState.value = HistoryState(entries = currentEntries)
    }
    
    /**
     * Clear execution history
     */
    fun clearHistory() {
        _historyState.value = HistoryState(entries = emptyList())
        addNotification(
            type = NotificationType.INFO,
            title = "History Cleared",
            message = "Execution history has been cleared"
        )
    }
    
    
    /**
     * Replay execution from history
     */
    suspend fun replayExecution(historyEntry: ExecutionHistoryEntry): Result<CallToolResult> {
        return callTool(historyEntry.toolName, historyEntry.parameters)
    }
    
    /**
     * Add notification
     */
    private fun addNotification(
        type: NotificationType, 
        title: String, 
        message: String, 
        category: NotificationCategory = NotificationCategory.SERVER
    ) {
        val notification = ServerNotification(
            id = generateRequestId(),
            timestamp = System.currentTimeMillis(),
            type = type,
            title = title,
            message = message,
            category = category,
            isRead = false
        )
        
        val currentNotifications = _notificationsState.value.notifications.toMutableList()
        currentNotifications.add(0, notification) // Add to beginning for newest first
        
        // Keep only last 50 notifications
        if (currentNotifications.size > 50) {
            currentNotifications.removeAt(currentNotifications.size - 1)
        }
        
        val unreadCount = currentNotifications.count { !it.isRead }
        
        _notificationsState.value = NotificationsState(
            notifications = currentNotifications,
            unreadCount = unreadCount
        )
    }
    
    /**
     * Mark notification as read
     */
    fun markNotificationAsRead(notificationId: String) {
        val updatedNotifications = _notificationsState.value.notifications.map { notification ->
            if (notification.id == notificationId) {
                notification.copy(isRead = true)
            } else {
                notification
            }
        }
        
        val unreadCount = updatedNotifications.count { !it.isRead }
        
        _notificationsState.value = NotificationsState(
            notifications = updatedNotifications,
            unreadCount = unreadCount
        )
    }
    
    /**
     * Mark all notifications as read
     */
    fun markAllNotificationsAsRead() {
        val updatedNotifications = _notificationsState.value.notifications.map { notification ->
            notification.copy(isRead = true)
        }
        
        _notificationsState.value = NotificationsState(
            notifications = updatedNotifications,
            unreadCount = 0
        )
    }
    
    /**
     * Clear all notifications
     */
    fun clearNotifications() {
        _notificationsState.value = NotificationsState()
    }
    
    
    /**
     * Check connection health and attempt to reconnect if needed
     */
    suspend fun checkConnectionHealth(): Boolean {
        return try {
            if (!_connectionState.value.isConnected) {
                println("McpClient: Connection health check - not connected")
                return false
            }
            
            if (sseJob?.isActive != true) {
                println("McpClient: Connection health check - SSE job not active, attempting reconnect...")
                val currentUrl = _connectionState.value.serverUrl
                if (currentUrl.isNotEmpty()) {
                    val reconnectResult = connect(currentUrl)
                    if (reconnectResult.isSuccess) {
                        println("McpClient: Automatic reconnection successful")
                        addNotification(
                            type = NotificationType.SUCCESS,
                            title = "Reconnected",
                            message = "Successfully reconnected to server"
                        )
                        return true
                    } else {
                        println("McpClient: Automatic reconnection failed")
                        addNotification(
                            type = NotificationType.ERROR,
                            title = "Reconnection Failed",
                            message = "Failed to reconnect to server"
                        )
                        return false
                    }
                }
                return false
            }
            
            println("McpClient: Connection health check - OK")
            return true
        } catch (e: Exception) {
            println("McpClient: Connection health check failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Load tools with connection health check
     */
    suspend fun loadToolsWithHealthCheck(): Result<List<Tool>> {
        // First check connection health
        if (!checkConnectionHealth()) {
            val error = "Connection is not healthy. Please check server status and reconnect."
            _toolsState.value = _toolsState.value.copy(
                isLoading = false,
                error = error
            )
            return Result.failure(Exception(error))
        }
        
        // If connection is healthy, proceed with loading tools
        return loadTools()
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