package com.example.mcpinspector.ui

import kotlinx.serialization.json.*

/**
 * Helper classes for parsing tool input schema and managing parameter inputs
 */

/**
 * Represents a parameter field from the tool's input schema
 */
data class ParameterField(
    val name: String,
    val type: ParameterType,
    val description: String?,
    val required: Boolean,
    val defaultValue: String? = null,
    val enumValues: List<String>? = null
)

/**
 * Supported parameter types
 */
enum class ParameterType {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    ARRAY,
    OBJECT
}

/**
 * Parses JSON schema to extract parameter fields
 */
class SchemaParser {
    
    fun parseSchema(schema: JsonElement?): List<ParameterField> {
        if (schema == null) return emptyList()
        
        return try {
            val schemaObj = schema.jsonObject
            val properties = schemaObj["properties"]?.jsonObject ?: return emptyList()
            val required = schemaObj["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
            
            properties.map { (name, propertySchema) ->
                parseProperty(name, propertySchema, name in required)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseProperty(name: String, property: JsonElement, isRequired: Boolean): ParameterField {
        val propertyObj = property.jsonObject
        
        val type = when (propertyObj["type"]?.jsonPrimitive?.content) {
            "string" -> ParameterType.STRING
            "number" -> ParameterType.NUMBER
            "integer" -> ParameterType.INTEGER
            "boolean" -> ParameterType.BOOLEAN
            "array" -> ParameterType.ARRAY
            "object" -> ParameterType.OBJECT
            else -> ParameterType.STRING
        }
        
        val description = propertyObj["description"]?.jsonPrimitive?.content
        val defaultValue = propertyObj["default"]?.jsonPrimitive?.content
        
        val enumValues = propertyObj["enum"]?.jsonArray?.mapNotNull { 
            it.jsonPrimitive.contentOrNull 
        }
        
        return ParameterField(
            name = name,
            type = type,
            description = description,
            required = isRequired,
            defaultValue = defaultValue,
            enumValues = enumValues
        )
    }
}

/**
 * Manages parameter values and converts them to JSON
 */
class ParameterManager {
    private val _values = mutableMapOf<String, String>()
    val values: Map<String, String> get() = _values.toMap()
    
    fun setValue(fieldName: String, value: String) {
        _values[fieldName] = value
    }
    
    fun getValue(fieldName: String): String {
        return _values[fieldName] ?: ""
    }
    
    fun toJsonElement(fields: List<ParameterField>): JsonElement {
        val jsonObject = buildJsonObject {
            fields.forEach { field ->
                val value = _values[field.name]
                if (!value.isNullOrBlank() || field.required) {
                    val jsonValue = convertToJsonValue(value ?: "", field.type)
                    if (jsonValue != null) {
                        put(field.name, jsonValue)
                    }
                }
            }
        }
        return jsonObject
    }
    
    private fun convertToJsonValue(value: String, type: ParameterType): JsonElement? {
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
    
    fun clear() {
        _values.clear()
    }
}
