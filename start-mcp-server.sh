#!/bin/bash

# Start MCP Server Script
# This script starts the MCP server for testing the plugin

echo "Starting MCP Server..."
echo "Make sure you have installed the dependencies:"
echo "  pip install mcp fastmcp python-dotenv"
echo ""

cd simple-server-setup

# Check if Python is available
if ! command -v python &> /dev/null; then
    echo "Error: Python is not installed or not in PATH"
    exit 1
fi

# Check if required packages are installed
python -c "import mcp, fastmcp" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "Error: Required Python packages are not installed"
    echo "Please run: pip install mcp fastmcp python-dotenv"
    exit 1
fi

echo "Starting MCP server on http://localhost:8050..."
echo "Press Ctrl+C to stop the server"
echo ""

python server.py
