package com.example.mcpinspector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.model.Tool
import com.example.mcpinspector.model.ToolsState

/**
 * Tools pane for displaying and selecting available MCP tools
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsPane(
    toolsState: ToolsState,
    onToolSelected: (Tool) -> Unit,
    onRefreshTools: () -> Unit,
    onCheckHealth: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Header with title and action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Available Tools",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Health check button (if provided)
                onCheckHealth?.let { healthCheck ->
                    IconButton(
                        onClick = healthCheck,
                        enabled = !toolsState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = "Check Connection Health",
                            tint = if (toolsState.error != null) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Refresh button
                IconButton(
                    onClick = onRefreshTools,
                    enabled = !toolsState.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Tools"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Content area
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                toolsState.isLoading -> {
                    // Loading state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading tools...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                toolsState.error != null -> {
                    // Error state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Error loading tools",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = toolsState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefreshTools) {
                            Text("Retry")
                        }
                    }
                }
                
                toolsState.tools.isEmpty() -> {
                    // Empty state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "No tools",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No tools available",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connect to a server to see available tools",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    // Tools list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(toolsState.tools) { tool ->
                            ToolItem(
                                tool = tool,
                                isSelected = tool == toolsState.selectedTool,
                                onClick = { onToolSelected(tool) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual tool item in the list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolItem(
    tool: Tool,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Tool name
            Text(
                text = tool.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else MaterialTheme.colorScheme.onSurface
            )
            
            // Tool description
            tool.description?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
