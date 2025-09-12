package com.example.mcpinspector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.mcp.McpClient
import com.example.mcpinspector.model.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/**
 * Main Compose UI for the MCP Inspector Lite plugin
 */
@Composable
fun McpInspectorApp() {
    val mcpClient = remember { McpClient() }
    val scope = rememberCoroutineScope()
    
    // State management
    val connectionState by mcpClient.connectionState.collectAsState()
    val tools by mcpClient.tools.collectAsState()
    val lastError by mcpClient.lastError.collectAsState()
    val toolHistory by mcpClient.toolHistory.collectAsState()
    val notifications by mcpClient.notifications.collectAsState()
    
    var serverUrl by remember { mutableStateOf("http://localhost:3000") }
    var selectedTool by remember { mutableStateOf<McpTool?>(null) }
    var toolParameters by remember { mutableStateOf("{}") }
    // Store results per tool name
    val toolResults = remember { mutableStateMapOf<String, ToolInvocationHistory>() }
    var selectedTab by remember { mutableStateOf(0) } // 0: Tools, 1: History, 2: Notifications
    
    // Parameter management
    val schemaParser = remember { SchemaParser() }
    val parameterManager = remember { ParameterManager() }
    var parameterFields by remember { mutableStateOf<List<ParameterField>>(emptyList()) }
    var useSimpleInput by remember { mutableStateOf(true) }
    
    // State map for parameter values to ensure proper recomposition
    val parameterValues = remember { mutableStateMapOf<String, String>() }
    
    // Update parameter fields when tool changes
    LaunchedEffect(selectedTool) {
        selectedTool?.let { tool ->
            parameterFields = schemaParser.parseSchema(tool.inputSchema)
            parameterManager.clear()
            parameterValues.clear()
            useSimpleInput = schemaParser.requiresParameters(tool.inputSchema)
        }
    }
    
    // Clean up on disposal
    DisposableEffect(mcpClient) {
        onDispose {
            mcpClient.close()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection Pane
        ConnectionPane(
            serverUrl = serverUrl,
            onServerUrlChange = { serverUrl = it },
            connectionState = connectionState,
            lastError = lastError,
            onConnect = {
                scope.launch {
                    val config = McpServerConfig(
                        name = "Default Server",
                        url = serverUrl
                    )
                    mcpClient.connect(config)
                }
            },
            onDisconnect = {
                mcpClient.disconnect()
            },
            onRestart = {
                scope.launch {
                    mcpClient.restart()
                }
            }
        )
        
        Divider()
        
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane with Tabs
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Tools") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("History")
                                    if (toolHistory.isNotEmpty()) {
                                        Badge {
                                            Text("${toolHistory.size}")
                                        }
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Notifications")
                                    val unreadCount = notifications.count { !it.isRead }
                                    if (unreadCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ) {
                                            Text("$unreadCount")
                                        }
                                    }
                                }
                            }
                        )
                    }
                    
                    when (selectedTab) {
                        0 -> ToolsPane(
                            tools = tools,
                            selectedTool = selectedTool,
                            onToolSelected = { selectedTool = it },
                            connectionState = connectionState
                        )
                        1 -> HistoryPane(
                            history = toolHistory,
                            onClearHistory = { mcpClient.clearHistory() }
                        )
                        2 -> NotificationsPane(
                            notifications = notifications,
                            onMarkAsRead = { mcpClient.markNotificationAsRead(it) },
                            onClearNotifications = { mcpClient.clearNotifications() }
                        )
                    }
                }
            }
            
            // Details & Results Pane
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                DetailsAndResultsPane(
                    selectedTool = selectedTool,
                    toolParameters = toolParameters,
                    onParametersChange = { toolParameters = it },
                    currentResult = selectedTool?.let { toolResults[it.name] },
                    parameterFields = parameterFields,
                    parameterManager = parameterManager,
                    parameterValues = parameterValues,
                    useSimpleInput = useSimpleInput,
                    onToggleInputMode = { useSimpleInput = !useSimpleInput },
                    schemaParser = schemaParser,
                    onInvokeTool = {
                        selectedTool?.let { tool ->
                            scope.launch {
                                try {
                                    val requiresParams = schemaParser.requiresParameters(tool.inputSchema)
                                    val params = if (requiresParams) {
                                        if (useSimpleInput && parameterFields.isNotEmpty()) {
                                            convertParametersToJson(parameterFields, parameterValues)
                                        } else if (toolParameters.isBlank()) {
                                            null
                                        } else {
                                            Json.parseToJsonElement(toolParameters)
                                        }
                                    } else {
                                        null // Tool doesn't require parameters
                                    }
                                    
                                    val result = mcpClient.callTool(tool.name, params)
                                    result.getOrNull()?.let { historyEntry ->
                                        toolResults[tool.name] = historyEntry
                                    }
                                } catch (e: Exception) {
                                    // Error will be captured in the result
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ConnectionPane(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    connectionState: ConnectionState,
    lastError: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRestart: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Connection",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("Server URL") },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState == ConnectionState.DISCONNECTED
                )
                
                when (connectionState) {
                    ConnectionState.DISCONNECTED -> {
                        Button(
                            onClick = onConnect,
                            enabled = serverUrl.isNotBlank()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Connect")
                        }
                    }
                    ConnectionState.CONNECTING -> {
                        Button(
                            onClick = { },
                            enabled = false
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connecting...")
                        }
                    }
                    ConnectionState.CONNECTED -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRestart,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Restart")
                            }
                            Button(
                                onClick = onDisconnect,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Disconnect")
                            }
                        }
                    }
                    ConnectionState.ERROR -> {
                        Button(
                            onClick = onConnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }
            
            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val (statusIcon, statusText, statusColor) = when (connectionState) {
                    ConnectionState.DISCONNECTED -> Triple(
                        Icons.Default.RadioButtonUnchecked,
                        "Disconnected",
                        MaterialTheme.colorScheme.onSurface
                    )
                    ConnectionState.CONNECTING -> Triple(
                        Icons.Default.RadioButtonUnchecked,
                        "Connecting...",
                        MaterialTheme.colorScheme.primary
                    )
                    ConnectionState.CONNECTED -> Triple(
                        Icons.Default.CheckCircle,
                        "Connected",
                        MaterialTheme.colorScheme.primary
                    )
                    ConnectionState.ERROR -> Triple(
                        Icons.Default.Error,
                        "Error",
                        MaterialTheme.colorScheme.error
                    )
                }
                
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Error message
            lastError?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Help message when disconnected
            if (connectionState == ConnectionState.DISCONNECTED) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "💡 Quick Start",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "To start the MCP server, run in terminal:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "./dev-start.sh server",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Or with auto-reload: ./dev-start.sh server-watch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsPane(
    tools: List<McpTool>,
    selectedTool: McpTool?,
    onToolSelected: (McpTool) -> Unit,
    connectionState: ConnectionState
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Available Tools",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        when {
            connectionState != ConnectionState.CONNECTED -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Connect to a server to see available tools",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            tools.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tools available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tools) { tool ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedTool == tool) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            onClick = { onToolSelected(tool) }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = tool.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                tool.description?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsAndResultsPane(
    selectedTool: McpTool?,
    toolParameters: String,
    onParametersChange: (String) -> Unit,
    currentResult: ToolInvocationHistory?,
    parameterFields: List<ParameterField>,
    @Suppress("UNUSED_PARAMETER") parameterManager: ParameterManager,
    parameterValues: MutableMap<String, String>,
    useSimpleInput: Boolean,
    onToggleInputMode: () -> Unit,
    onInvokeTool: () -> Unit,
    schemaParser: SchemaParser
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Tool Details & Results",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        selectedTool?.let { tool ->
            // Tool details
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    tool.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Check if tool requires parameters
            val requiresParams = schemaParser.requiresParameters(tool.inputSchema)
            
            // Input mode toggle (only if schema has parameters)
            if (requiresParams && parameterFields.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Input Mode:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (useSimpleInput) "Simple Form" else "JSON",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = useSimpleInput,
                            onCheckedChange = { onToggleInputMode() }
                        )
                    }
                }
            }
            
            // Parameters input (only show if tool requires parameters)
            if (requiresParams) {
                if (useSimpleInput && parameterFields.isNotEmpty()) {
                    // Simple form input
                    Text(
                        text = "Parameters:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SimpleParameterInputForm(
                            fields = parameterFields,
                            parameterValues = parameterValues,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    
                } else {
                    // JSON input
                    OutlinedTextField(
                        value = toolParameters,
                        onValueChange = onParametersChange,
                        label = { Text("Parameters (JSON)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )
                    
                    // Show schema for reference
                    tool.inputSchema?.let { schema ->
                        Text(
                            text = "Schema Reference:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = schema.toString(),
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            } else {
                // Tool doesn't require parameters - show info message
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "This tool doesn't require any parameters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Invoke button
            Button(
                onClick = onInvokeTool,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Invoke Tool")
            }
            
            // Results
            currentResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                ToolResultCard(result = result)
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a tool to see details and invoke it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HistoryPane(
    history: List<ToolInvocationHistory>,
    onClearHistory: () -> Unit
) {
    var expandedItems by remember { mutableStateOf(setOf<String>()) }
    
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tool History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
        
        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tool invocations yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(history) { historyItem ->
                    HistoryItemCard(
                        historyItem = historyItem,
                        isExpanded = expandedItems.contains(historyItem.id),
                        onToggleExpanded = { 
                            expandedItems = if (expandedItems.contains(historyItem.id)) {
                                expandedItems - historyItem.id
                            } else {
                                expandedItems + historyItem.id
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    historyItem: ToolInvocationHistory,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header - always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Expand/collapse icon
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        text = historyItem.toolName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (historyItem.result) {
                        is ToolInvocationResult.Success -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        is ToolInvocationResult.Error -> {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    historyItem.duration?.let { duration ->
                        Text(
                            text = "${duration}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Timestamp - always visible
            Text(
                text = java.text.SimpleDateFormat("HH:mm:ss dd.MM.yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(historyItem.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Expanded content
            if (isExpanded) {
                Divider()
                
                // Parameters section
                Text(
                    text = "Parameters:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = if (historyItem.parameters != null) {
                            historyItem.parameters.toString()
                        } else {
                            "No parameters"
                        },
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                
                // Result section
                Text(
                    text = "Result:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                
                when (val result = historyItem.result) {
                    is ToolInvocationResult.Success -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "SUCCESS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                result.content?.let { content ->
                                    if (content.isNotEmpty()) {
                                        Text(
                                            text = "Content:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        content.take(3).forEach { contentItem -> // Show max 3 items
                                            Text(
                                                text = (contentItem.text ?: contentItem.data ?: "No content").take(200) + 
                                                    if ((contentItem.text ?: contentItem.data ?: "").length > 200) "..." else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (content.size > 3) {
                                            Text(
                                                text = "... and ${content.size - 3} more items",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is ToolInvocationResult.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ERROR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                
                                result.code?.let { code ->
                                    Text(
                                        text = "Error Code: $code",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                
                                result.details?.let { details ->
                                    Text(
                                        text = "Details:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = details.take(300) + if (details.length > 300) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsPane(
    notifications: List<ServerNotification>,
    onMarkAsRead: (String) -> Unit,
    onClearNotifications: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            if (notifications.isNotEmpty()) {
                TextButton(onClick = onClearNotifications) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
        
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No notifications",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(notifications) { notification ->
                    NotificationCard(
                        notification = notification,
                        onMarkAsRead = { onMarkAsRead(notification.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: ServerNotification,
    onMarkAsRead: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            }
        ),
        onClick = if (!notification.isRead) onMarkAsRead else { {} }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (icon, color) = when (notification.type) {
                        NotificationType.INFO -> Icons.Default.Info to MaterialTheme.colorScheme.primary
                        NotificationType.WARNING -> Icons.Default.Warning to MaterialTheme.colorScheme.tertiary
                        NotificationType.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
                        NotificationType.TOOLS_CHANGED -> Icons.Default.Build to MaterialTheme.colorScheme.primary
                        NotificationType.CONNECTION_LOST -> Icons.Default.WifiOff to MaterialTheme.colorScheme.error
                        NotificationType.CONNECTION_RESTORED -> Icons.Default.Wifi to MaterialTheme.colorScheme.primary
                    }
                    
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                if (!notification.isRead) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text("NEW")
                    }
                }
            }
            
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(notification.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ToolResultCard(result: ToolInvocationHistory) {
    Text(
        text = "Result:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result.result) {
                is ToolInvocationResult.Success -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                is ToolInvocationResult.Error -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with status and timing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (result.result) {
                        is ToolInvocationResult.Success -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "SUCCESS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ToolInvocationResult.Error -> {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "ERROR",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                result.duration?.let { duration ->
                    Text(
                        text = "Executed in ${duration}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Divider()
            
            // Result content
            when (val resultData = result.result) {
                is ToolInvocationResult.Success -> {
                    Text(
                        text = resultData.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    resultData.content?.let { content ->
                        if (content.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            content.forEach { contentItem ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Text(
                                        text = contentItem.text ?: contentItem.data ?: "No content",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
                is ToolInvocationResult.Error -> {
                    Text(
                        text = resultData.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    resultData.code?.let { code ->
                        Text(
                            text = "Error Code: $code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    
                    resultData.details?.let { details ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = details,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
