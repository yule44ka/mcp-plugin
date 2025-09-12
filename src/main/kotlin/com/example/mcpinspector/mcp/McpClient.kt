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
import kotlin.system.measureTimeMillis

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
    
    // History management
    private val _toolHistory = MutableStateFlow<List<ToolInvocationHistory>>(emptyList())
    val toolHistory: StateFlow<List<ToolInvocationHistory>> = _toolHistory.asStateFlow()
    
    // Notifications management
    private val _notifications = MutableStateFlow<List<ServerNotification>>(emptyList())
    val notifications: StateFlow<List<ServerNotification>> = _notifications.asStateFlow()
    
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
                addNotification(
                    NotificationType.INFO,
                    "Connected",
                    "Successfully connected to ${serverConfig.name}"
                )
                Result.success(Unit)
            } else {
                _connectionState.value = ConnectionState.ERROR
                _lastError.value = tools.exceptionOrNull()?.message ?: "Failed to connect"
                addNotification(
                    NotificationType.ERROR,
                    "Connection Failed",
                    "Failed to connect to ${serverConfig.name}: ${tools.exceptionOrNull()?.message}"
                )
                Result.failure(tools.exceptionOrNull() ?: Exception("Connection failed"))
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            _lastError.value = e.message ?: "Unknown error"
            addNotification(
                NotificationType.ERROR,
                "Connection Error",
                "Connection error: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from the MCP server
     */
    fun disconnect() {
        val serverName = currentServerConfig?.name ?: "Server"
        connectionJob?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
        _tools.value = emptyList()
        _lastError.value = null
        currentServerConfig = null
        
        addNotification(
            NotificationType.INFO,
            "Disconnected",
            "Disconnected from $serverName"
        )
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
    suspend fun callTool(toolName: String, arguments: JsonElement?): Result<ToolInvocationHistory> {
        val serverConfig = currentServerConfig ?: return Result.failure(Exception("Not connected"))
        
        val historyId = generateRequestId()
        val startTime = System.currentTimeMillis()
        
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
            
            var result: ToolInvocationResult
            
            val duration = measureTimeMillis {
                val response: JsonRpcResponse = httpClient.post(serverConfig.url) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()
                
                result = if (response.error != null) {
                    ToolInvocationResult.Error(
                        message = response.error.message,
                        code = response.error.code,
                        details = response.error.data?.toString()
                    )
                } else {
                    val toolCallResponse = Json.decodeFromJsonElement<McpToolCallResponse>(
                        response.result ?: JsonObject(emptyMap())
                    )
                    
                    if (toolCallResponse.isError == true) {
                        ToolInvocationResult.Error(
                            message = "Tool execution failed",
                            details = toolCallResponse.content?.joinToString("\n") { it.text ?: it.data ?: "No content" }
                        )
                    } else {
                        ToolInvocationResult.Success(
                            content = toolCallResponse.content,
                            message = if (toolCallResponse.content?.isNotEmpty() == true) {
                                "Tool executed successfully"
                            } else {
                                "Tool executed successfully (no content returned)"
                            }
                        )
                    }
                }
            }
            
            val historyEntry = ToolInvocationHistory(
                id = historyId,
                toolName = toolName,
                parameters = arguments,
                timestamp = startTime,
                result = result,
                duration = duration
            )
            
            // Add to history
            addToHistory(historyEntry)
            
            Result.success(historyEntry)
        } catch (e: Exception) {
            val errorResult = ToolInvocationResult.Error(
                message = e.message ?: "Unknown error",
                details = e.stackTraceToString()
            )
            
            val historyEntry = ToolInvocationHistory(
                id = historyId,
                toolName = toolName,
                parameters = arguments,
                timestamp = startTime,
                result = errorResult,
                duration = System.currentTimeMillis() - startTime
            )
            
            addToHistory(historyEntry)
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
     * Add entry to tool invocation history
     */
    private fun addToHistory(entry: ToolInvocationHistory) {
        val currentHistory = _toolHistory.value.toMutableList()
        currentHistory.add(0, entry) // Add to beginning for chronological order
        
        // Keep only last 100 entries
        if (currentHistory.size > 100) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        _toolHistory.value = currentHistory
    }
    
    /**
     * Clear tool invocation history
     */
    fun clearHistory() {
        _toolHistory.value = emptyList()
    }
    
    /**
     * Add server notification
     */
    fun addNotification(type: NotificationType, title: String, message: String) {
        val notification = ServerNotification(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = type,
            title = title,
            message = message
        )
        
        val currentNotifications = _notifications.value.toMutableList()
        currentNotifications.add(0, notification) // Add to beginning
        
        // Keep only last 50 notifications
        if (currentNotifications.size > 50) {
            currentNotifications.removeAt(currentNotifications.size - 1)
        }
        
        _notifications.value = currentNotifications
    }
    
    /**
     * Mark notification as read
     */
    fun markNotificationAsRead(notificationId: String) {
        val currentNotifications = _notifications.value.toMutableList()
        val index = currentNotifications.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            currentNotifications[index] = currentNotifications[index].copy(isRead = true)
            _notifications.value = currentNotifications
        }
    }
    
    /**
     * Clear all notifications
     */
    fun clearNotifications() {
        _notifications.value = emptyList()
    }
    
    /**
     * Get unread notifications count
     */
    fun getUnreadNotificationsCount(): Int {
        return _notifications.value.count { !it.isRead }
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        disconnect()
        httpClient.close()
    }
}
