package com.example.mcpinspector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.mcp.McpClient
import kotlinx.coroutines.launch

/**
 * Main MCP Inspector Lite application UI
 * Provides a three-pane interface for MCP server interaction
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpInspectorApp() {
    val mcpClient = remember { McpClient() }
    val scope = rememberCoroutineScope()
    
    // Collect state from the MCP client
    val connectionState by mcpClient.connectionState.collectAsState()
    val toolsState by mcpClient.toolsState.collectAsState()
    val executionState by mcpClient.executionState.collectAsState()
    
    // Clean up client when composable is disposed
    DisposableEffect(mcpClient) {
        onDispose {
            mcpClient.dispose()
        }
    }
    
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Connection Pane (Top)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    ConnectionPane(
                        connectionState = connectionState,
                        onConnect = { serverUrl ->
                            scope.launch {
                                mcpClient.connect(serverUrl)
                            }
                        },
                        onDisconnect = {
                            scope.launch {
                                mcpClient.disconnect()
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Main content area with Tools and Details panes
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Tools Pane (Left)
                    Card(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        ToolsPane(
                            toolsState = toolsState,
                            onToolSelected = { tool ->
                                mcpClient.selectTool(tool)
                            },
                            onRefreshTools = {
                                scope.launch {
                                    mcpClient.loadTools()
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Details & Results Pane (Right)
                    Card(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        DetailsPane(
                            selectedTool = toolsState.selectedTool,
                            executionState = executionState,
                            onParametersChanged = { parameters ->
                                mcpClient.updateParameters(parameters)
                            },
                            onExecuteTool = { toolName, parameters ->
                                scope.launch {
                                    mcpClient.callTool(toolName, parameters)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
