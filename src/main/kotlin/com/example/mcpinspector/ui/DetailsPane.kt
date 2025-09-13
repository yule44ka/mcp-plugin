package com.example.mcpinspector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.model.ExecutionState
import com.example.mcpinspector.model.Tool
import com.example.mcpinspector.model.ParameterInputMode
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
    onExecuteTool: (String, Map<String, Any>) -> Unit,
    onToggleInputMode: () -> Unit = {}
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
                onExecuteTool = onExecuteTool,
                onToggleInputMode = onToggleInputMode
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
    onExecuteTool: (String, Map<String, Any>) -> Unit,
    onToggleInputMode: () -> Unit
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
        
        // Parameters section with input mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Parameters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Input mode toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (executionState.inputMode == ParameterInputMode.FORM) "Form" else "JSON",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                IconButton(
                    onClick = onToggleInputMode,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (executionState.inputMode == ParameterInputMode.FORM) {
                            Icons.Default.Code
                        } else {
                            Icons.Default.ViewList
                        },
                        contentDescription = "Toggle input mode",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // Parse schema once for both modes
        val inputSchema = tool.inputSchema
        val properties = inputSchema["properties"]?.jsonObject
        val required = inputSchema["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        
        // Parameter input based on mode
        if (executionState.inputMode == ParameterInputMode.FORM) {
            // Form mode - auto-generated forms based on tool schema
            
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
        } else {
            // JSON mode - direct JSON input
            var jsonInput by remember(tool) {
                val jsonString = if (parameters.isEmpty()) {
                    "{}"
                } else {
                    buildJsonObject {
                        parameters.forEach { (key, value) ->
                            put(key, value)
                        }
                    }.toString()
                }
                mutableStateOf(jsonString)
            }
            
            var jsonError by remember { mutableStateOf<String?>(null) }
            
            Column {
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { newValue ->
                        jsonInput = newValue
                        try {
                            // Validate JSON and update parameters
                            val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(newValue)
                            if (jsonElement is JsonObject) {
                                val newParams = mutableMapOf<String, String>()
                                jsonElement.forEach { (key, value) ->
                                    newParams[key] = when (value) {
                                        is JsonPrimitive -> value.content
                                        else -> value.toString()
                                    }
                                }
                                parameters = newParams
                                jsonError = null
                            } else {
                                jsonError = "Root must be a JSON object"
                            }
                        } catch (e: Exception) {
                            jsonError = "Invalid JSON: ${e.message}"
                        }
                    },
                    label = { Text("Parameters (JSON)") },
                    placeholder = { Text("{}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    isError = jsonError != null,
                    supportingText = jsonError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                
                // Schema reference
                Text(
                    text = "Schema: ${tool.inputSchema}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Result: Error",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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
                val isError = executionState.result.isError == true
                val containerColor = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                val contentColor = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val iconColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                contentDescription = if (isError) "Error" else "Success",
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isError) "Result: Error" else "Result: Success",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        executionState.result.content.forEach { content ->
                            when (content.type) {
                                "text" -> {
                                    content.text?.let { text ->
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = contentColor,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = "Content type: ${content.type}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentColor
                                    )
                                    content.data?.let { data ->
                                        Text(
                                            text = data,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = contentColor,
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
