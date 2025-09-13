package com.example.mcpinspector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.model.ConnectionState

/**
 * Connection pane for managing MCP server connections
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionPane(
    connectionState: ConnectionState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    var serverUrl by remember { mutableStateOf(connectionState.serverUrl) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title
        Text(
            text = "MCP Server Connection",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        // Server URL input and connection controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Server URL input
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://localhost:8050/sse") },
                enabled = !connectionState.isConnected,
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            
            // Connect/Disconnect button
            if (connectionState.isConnected) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = "Disconnect",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = { onConnect(serverUrl) },
                    enabled = serverUrl.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Connect",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connect")
                }
            }
        }
        
        // Status and server info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(2.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (connectionState.isConnected) 
                                Color.Green else Color.Red
                        )
                    ) {}
                }
                
                Text(
                    text = connectionState.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connectionState.isConnected) 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.error
                )
            }
            
            // Server info
            connectionState.serverInfo?.let { serverInfo ->
                Text(
                    text = "${serverInfo.name} v${serverInfo.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
