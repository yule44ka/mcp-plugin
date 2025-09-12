package com.example.mcpinspector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*

/**
 * Simplified parameter input components using MutableStateMap for better Compose integration
 */

@Composable
fun SimpleParameterInputForm(
    fields: List<ParameterField>,
    parameterValues: MutableMap<String, String>,
    validationErrors: Map<String, String> = emptyMap(),
    onValueChange: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(fields) { field ->
            SimpleParameterFieldInput(
                field = field,
                value = parameterValues[field.name] ?: "",
                onValueChange = { newValue ->
                    parameterValues[field.name] = newValue
                    onValueChange?.invoke(field.name, newValue)
                },
                validationError = validationErrors[field.name]
            )
        }
    }
}

@Composable
fun SimpleParameterFieldInput(
    field: ParameterField,
    value: String,
    onValueChange: (String) -> Unit,
    validationError: String? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Field label with required indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = field.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            if (field.required) {
                Text(
                    text = "*",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            
        }
        
        // Description text
        field.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Input field based on type
        when (field.type) {
            ParameterType.STRING -> {
                if (field.enumValues != null) {
                    SimpleEnumDropdown(
                        options = field.enumValues,
                        selectedValue = value,
                        onValueChange = onValueChange,
                        placeholder = "Select ${field.name}"
                    )
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { 
                            Text(field.defaultValue ?: "Enter ${field.name}") 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = validationError != null,
                        supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                }
            }
            
            ParameterType.NUMBER, ParameterType.INTEGER -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { 
                        Text(field.defaultValue ?: "Enter number") 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
            }
            
            ParameterType.BOOLEAN -> {
                SimpleBooleanSwitch(
                    checked = value.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { onValueChange(it.toString()) }
                )
            }
            
            ParameterType.ARRAY -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { 
                        Text("Enter comma-separated values") 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
            }
            
            ParameterType.OBJECT -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { 
                        Text("Enter JSON object") 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
            }
        }
    }
}

@Composable
fun SimpleEnumDropdown(
    options: List<String>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = { },
            readOnly = true,
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SimpleBooleanSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = checked,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(vertical = 8.dp)
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        
        Text(
            text = if (checked) "True" else "False",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


/**
 * Convert parameter values to JSON element
 */
fun convertParametersToJson(
    fields: List<ParameterField>,
    parameterValues: Map<String, String>
): JsonElement {
    return buildJsonObject {
        fields.forEach { field ->
            val value = parameterValues[field.name]
            if (!value.isNullOrBlank() || field.required) {
                val jsonValue = convertValueToJson(value ?: "", field.type)
                if (jsonValue != null) {
                    put(field.name, jsonValue)
                }
            }
        }
    }
}

private fun convertValueToJson(value: String, type: ParameterType): JsonElement? {
    if (value.isBlank()) return null
    
    return try {
        when (type) {
            ParameterType.STRING -> JsonPrimitive(value)
            ParameterType.NUMBER -> JsonPrimitive(value.toDouble())
            ParameterType.INTEGER -> JsonPrimitive(value.toInt())
            ParameterType.BOOLEAN -> JsonPrimitive(value.toBoolean())
            ParameterType.ARRAY -> {
                // Simple array parsing - comma separated values
                val items = value.split(",").map { it.trim() }
                JsonArray(items.map { JsonPrimitive(it) })
            }
            ParameterType.OBJECT -> {
                // Try to parse as JSON object
                Json.parseToJsonElement(value)
            }
        }
    } catch (e: Exception) {
        // If conversion fails, return as string
        JsonPrimitive(value)
    }
}
