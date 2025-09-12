package com.example.mcpinspector.ui

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
    
    var serverUrl by remember { mutableStateOf("http://localhost:3000") }
    var selectedTool by remember { mutableStateOf<McpTool?>(null) }
    var toolParameters by remember { mutableStateOf("{}") }
    var toolResult by remember { mutableStateOf<String?>(null) }
    
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
            useSimpleInput = parameterFields.isNotEmpty()
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
            }
        )
        
        Divider()
        
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tools Pane
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ToolsPane(
                    tools = tools,
                    selectedTool = selectedTool,
                    onToolSelected = { selectedTool = it },
                    connectionState = connectionState
                )
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
                    toolResult = toolResult,
                    parameterFields = parameterFields,
                    parameterManager = parameterManager,
                    parameterValues = parameterValues,
                    useSimpleInput = useSimpleInput,
                    onToggleInputMode = { useSimpleInput = !useSimpleInput },
                    onInvokeTool = {
                        selectedTool?.let { tool ->
                            scope.launch {
                                try {
                                    val params = if (useSimpleInput && parameterFields.isNotEmpty()) {
                                        convertParametersToJson(parameterFields, parameterValues)
                                    } else if (toolParameters.isBlank()) {
                                        null
                                    } else {
                                        Json.parseToJsonElement(toolParameters)
                                    }
                                    
                                    val result = mcpClient.callTool(tool.name, params)
                                    toolResult = if (result.isSuccess) {
                                        val response = result.getOrNull()
                                        if (response?.content?.isNotEmpty() == true) {
                                            // Extract text content from the response
                                            response.content.joinToString("\n") { content ->
                                                content.text ?: content.data ?: "No content"
                                            }
                                        } else {
                                            "Tool executed successfully (no content returned)"
                                        }
                                    } else {
                                        "Error: ${result.exceptionOrNull()?.message}"
                                    }
                                } catch (e: Exception) {
                                    toolResult = "Error parsing parameters: ${e.message}"
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
    onDisconnect: () -> Unit
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
    toolResult: String?,
    parameterFields: List<ParameterField>,
    parameterManager: ParameterManager,
    parameterValues: MutableMap<String, String>,
    useSimpleInput: Boolean,
    onToggleInputMode: () -> Unit,
    onInvokeTool: () -> Unit
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
            
            // Input mode toggle (if schema is available)
            if (parameterFields.isNotEmpty()) {
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
            
            // Parameters input
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
                
                // Parameter summary
                if (parameterValues.any { it.value.isNotBlank() }) {
                    SimpleParameterSummaryCard(
                        fields = parameterFields,
                        parameterValues = parameterValues
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
            toolResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Result:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.startsWith("Error:")) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.startsWith("Error:")) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
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
