#!/bin/bash

# Start MCP Server Script (Simple Version)
# This script starts the MCP server for testing the plugin
# For auto-reload functionality, use ./run-server-watch.sh instead

echo "🚀 Starting MCP Server (Simple Mode)"
echo "===================================="
echo ""
echo "💡 Tip: For auto-reload on file changes, use ./run-server-watch.sh"
echo ""

# Check if we're in the right directory
if [ ! -d "simple-server-setup" ]; then
    echo "❌ Error: simple-server-setup directory not found. Please run this script from the project root."
    exit 1
fi

echo "📦 Checking dependencies..."
echo "Make sure you have installed the dependencies:"
echo "  pip install mcp fastmcp python-dotenv"
echo ""

cd simple-server-setup

# Check if Python is available
if ! command -v python &> /dev/null; then
    echo "❌ Error: Python is not installed or not in PATH"
    exit 1
fi

# Check if required packages are installed
python -c "import mcp, fastmcp" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "❌ Error: Required Python packages are not installed"
    echo "Please run: pip install mcp fastmcp python-dotenv"
    exit 1
fi

echo "✅ All dependencies available!"
echo "🚀 Starting MCP server on http://localhost:8050..."
echo "🛑 Press Ctrl+C to stop the server"
echo ""

python server.py
