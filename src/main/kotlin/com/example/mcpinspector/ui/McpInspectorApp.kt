package com.example.mcpinspector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
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
    val historyState by mcpClient.historyState.collectAsState()
    val notificationsState by mcpClient.notificationsState.collectAsState()
    
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
                        },
                        onRestart = {
                            scope.launch {
                                mcpClient.restart()
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Main content area
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Tools/History/Notifications Pane (Left)
                    Card(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        var selectedTab by remember { mutableIntStateOf(0) }
                        
                        Column {
                            TabRow(selectedTabIndex = selectedTab) {
                                // Tools tab
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { 
                                        Row(
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text("Tools")
                                        }
                                    }
                                )
                                
                                // History tab
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { 
                                        Row(
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text("History")
                                        }
                                    }
                                )
                                
                                // Notifications tab
                                Tab(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    text = { 
                                        Row(
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text("Notifications")
                                        }
                                    }
                                )
                            }
                            
                            // Tab content
                            when (selectedTab) {
                                0 -> ToolsPane(
                                    toolsState = toolsState,
                                    onToolSelected = { tool ->
                                        mcpClient.selectTool(tool)
                                    },
                                    onRefreshTools = {
                                        scope.launch {
                                            mcpClient.loadToolsWithHealthCheck()
                                        }
                                    },
                                    onCheckHealth = {
                                        scope.launch {
                                            mcpClient.checkConnectionHealth()
                                        }
                                    }
                                )
                                1 -> HistoryPane(
                                    historyState = historyState,
                                    onReplayExecution = { entry ->
                                        scope.launch {
                                            mcpClient.replayExecution(entry)
                                        }
                                    },
                                    onClearHistory = {
                                        mcpClient.clearHistory()
                                    }
                                )
                                2 -> NotificationsPane(
                                    notificationsState = notificationsState,
                                    onClearNotifications = {
                                        mcpClient.clearNotifications()
                                    }
                                )
                            }
                        }
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
                            },
                            onToggleInputMode = {
                                mcpClient.toggleInputMode()
                            }
                        )
                    }
                }
            }
        }
    }
}
