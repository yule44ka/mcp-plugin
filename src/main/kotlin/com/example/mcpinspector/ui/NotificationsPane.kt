package com.example.mcpinspector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.model.ServerNotification
import com.example.mcpinspector.model.NotificationsState
import com.example.mcpinspector.model.NotificationType
import com.example.mcpinspector.model.NotificationCategory
import java.text.SimpleDateFormat
import java.util.*

/**
 * Notifications pane for displaying server messages and notifications
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsPane(
    notificationsState: NotificationsState,
    onClearNotifications: () -> Unit
) {
    // Filter to show only server notifications
    val serverNotifications = notificationsState.notifications.filter { 
        it.category == NotificationCategory.SERVER 
    }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Clear all button
            if (serverNotifications.isNotEmpty()) {
                IconButton(
                    onClick = onClearNotifications
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear All"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Content area
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                serverNotifications.isEmpty() -> {
                    // Empty state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "No notifications",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No notifications",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Server messages will appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    // Notifications list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(serverNotifications) { notification ->
                            NotificationItem(
                                notification = notification
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual notification item
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationItem(
    notification: ServerNotification
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeString = dateFormat.format(Date(notification.timestamp))
    
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = when (notification.type) {
                NotificationType.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                NotificationType.WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                NotificationType.SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                NotificationType.INFO -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Type icon
            Icon(
                imageVector = when (notification.type) {
                    NotificationType.ERROR -> Icons.Default.Error
                    NotificationType.WARNING -> Icons.Default.Warning
                    NotificationType.SUCCESS -> Icons.Default.CheckCircle
                    NotificationType.INFO -> Icons.Default.Info
                },
                contentDescription = notification.type.name,
                tint = when (notification.type) {
                    NotificationType.ERROR -> MaterialTheme.colorScheme.error
                    NotificationType.WARNING -> MaterialTheme.colorScheme.tertiary
                    NotificationType.SUCCESS -> MaterialTheme.colorScheme.primary
                    NotificationType.INFO -> MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.size(20.dp)
            )
            
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title and timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Message
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
