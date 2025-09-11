#!/bin/bash
# Simple script to start the MCP test server

echo "Starting MCP Test Server..."
echo "Server will be available at http://localhost:3000"
echo "Press Ctrl+C to stop the server"
echo ""

# Check if Python 3 is available
if ! command -v python3 &> /dev/null; then
    echo "Error: python3 is not installed or not in PATH"
    exit 1
fi

# Start the server
python3 mcp_server.py --port 3000 --host localhost
