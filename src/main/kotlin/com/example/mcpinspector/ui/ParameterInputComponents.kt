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

/**
 * Composable components for parameter input
 */

@Composable
fun ParameterInputForm(
    fields: List<ParameterField>,
    parameterManager: ParameterManager,
    validationErrors: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    // Create local state for each field to ensure proper recomposition
    val fieldStates = remember(fields) {
        fields.associate { field ->
            field.name to mutableStateOf(parameterManager.getValue(field.name))
        }
    }
    
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(fields) { field ->
            val fieldState = fieldStates[field.name] ?: return@items
            
            ParameterFieldInput(
                field = field,
                value = fieldState.value,
                onValueChange = { newValue ->
                    fieldState.value = newValue
                    parameterManager.setValue(field.name, newValue)
                },
                validationError = validationErrors[field.name]
            )
        }
    }
}

@Composable
fun ParameterFieldInput(
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
                    EnumDropdown(
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
                BooleanSwitch(
                    checked = value.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { onValueChange(it.toString()) },
                    label = field.name
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
fun EnumDropdown(
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
fun BooleanSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") label: String
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

