package com.example.mcpinspector.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import androidx.compose.ui.awt.ComposePanel
import javax.swing.JComponent

/**
 * Factory for creating the MCP Inspector Lite tool window
 */
class McpToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mcpInspectorPanel = createMcpInspectorPanel()
        val content = ContentFactory.getInstance().createContent(
            mcpInspectorPanel,
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
    
    private fun createMcpInspectorPanel(): JComponent {
        return ComposePanel().apply {
            setContent {
                McpInspectorApp()
            }
        }
    }
}
