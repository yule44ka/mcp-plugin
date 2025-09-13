package com.example.mcpinspector.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import androidx.compose.ui.awt.ComposePanel

/**
 * Factory for creating the MCP Inspector Lite tool window
 */
class McpToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        
        // Create the main Compose UI panel
        val composePanel = ComposePanel().apply {
            setContent {
                McpInspectorApp()
            }
        }
        
        // Create and add content to the tool window
        val content = contentFactory.createContent(
            composePanel,
            "MCP Inspector",
            false
        )
        
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}
