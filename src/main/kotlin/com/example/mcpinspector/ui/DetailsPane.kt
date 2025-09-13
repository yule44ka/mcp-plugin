package com.example.mcpinspector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.model.ExecutionState
import com.example.mcpinspector.model.Tool
import kotlinx.serialization.json.*

/**
 * Details pane for tool parameters input and execution results
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsPane(
    selectedTool: Tool?,
    executionState: ExecutionState,
    onParametersChanged: (Map<String, String>) -> Unit,
    onExecuteTool: (String, Map<String, Any>) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        if (selectedTool == null) {
            // No tool selected state
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "No tool selected",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No tool selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select a tool from the list to see its details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Tool details and execution
            ToolDetails(
                tool = selectedTool,
                executionState = executionState,
                onParametersChanged = onParametersChanged,
                onExecuteTool = onExecuteTool
            )
        }
    }
}

/**
 * Tool details with parameters and execution
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolDetails(
    tool: Tool,
    executionState: ExecutionState,
    onParametersChanged: (Map<String, String>) -> Unit,
    onExecuteTool: (String, Map<String, Any>) -> Unit
) {
    var parameters by remember(tool) { 
        mutableStateOf(executionState.parameters.toMutableMap()) 
    }
    
    // Update parameters when they change
    LaunchedEffect(parameters) {
        onParametersChanged(parameters)
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tool header
        Text(
            text = tool.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        tool.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Divider()
        
        // Parameters section
        Text(
            text = "Parameters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        // Parse and display input schema
        val inputSchema = tool.inputSchema
        val properties = inputSchema["properties"]?.jsonObject
        val required = inputSchema["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        
        if (properties != null && properties.isNotEmpty()) {
            properties.forEach { (paramName, paramSchema) ->
                val paramObj = paramSchema.jsonObject
                val paramType = paramObj["type"]?.jsonPrimitive?.content ?: "string"
                val paramDescription = paramObj["description"]?.jsonPrimitive?.content
                val isRequired = paramName in required
                
                ParameterInput(
                    name = paramName,
                    type = paramType,
                    description = paramDescription,
                    isRequired = isRequired,
                    value = parameters[paramName] ?: "",
                    onValueChange = { newValue ->
                        parameters = parameters.toMutableMap().apply {
                            if (newValue.isBlank()) {
                                remove(paramName)
                            } else {
                                put(paramName, newValue)
                            }
                        }
                    }
                )
            }
        } else {
            Text(
                text = "No parameters required",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Execute button
        Button(
            onClick = {
                val convertedParams = parameters.mapValues { (key, value) ->
                    // Try to convert to appropriate type based on schema
                    val paramSchema = properties?.get(key)?.jsonObject
                    val paramType = paramSchema?.get("type")?.jsonPrimitive?.content ?: "string"
                    
                    when (paramType) {
                        "integer" -> value.toIntOrNull() ?: value
                        "number" -> value.toDoubleOrNull() ?: value
                        "boolean" -> value.toBooleanStrictOrNull() ?: value
                        else -> value
                    }
                }
                onExecuteTool(tool.name, convertedParams)
            },
            enabled = !executionState.isExecuting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (executionState.isExecuting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Executing...")
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Execute",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Execute Tool")
            }
        }
        
        // Results section
        if (executionState.result != null || executionState.error != null) {
            Divider()
            
            Text(
                text = "Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (executionState.error != null) {
                // Error result
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = executionState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else if (executionState.result != null) {
                // Success result
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Result",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        executionState.result.content.forEach { content ->
                            when (content.type) {
                                "text" -> {
                                    content.text?.let { text ->
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = "Content type: ${content.type}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    content.data?.let { data ->
                                        Text(
                                            text = data,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontFamily = FontFamily.Monospace
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
}

/**
 * Parameter input field
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterInput(
    name: String,
    type: String,
    description: String?,
    isRequired: Boolean,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { 
                Text(
                    text = if (isRequired) "$name *" else name
                ) 
            },
            placeholder = { 
                Text(
                    text = when (type) {
                        "integer" -> "Enter a number"
                        "number" -> "Enter a decimal number"
                        "boolean" -> "true or false"
                        else -> "Enter $type value"
                    }
                ) 
            },
            supportingText = description?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = type != "string" || (description?.contains("multiline") != true)
        )
    }
}
