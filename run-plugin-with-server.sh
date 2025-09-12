#!/bin/bash

# MCP Inspector Lite - Run Plugin with Server
# This script starts the MCP test server and then runs the plugin

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SERVER_PORT=3000
SERVER_HOST=localhost
SERVER_URL="http://${SERVER_HOST}:${SERVER_PORT}"
SERVER_PID_FILE="/tmp/mcp_server.pid"

# Function to print colored messages
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to check if server is running
is_server_running() {
    if [ -f "$SERVER_PID_FILE" ]; then
        local pid=$(cat "$SERVER_PID_FILE")
        if ps -p "$pid" > /dev/null 2>&1; then
            return 0
        else
            rm -f "$SERVER_PID_FILE"
            return 1
        fi
    fi
    return 1
}

# Function to start MCP server
start_server() {
    print_message $BLUE "🚀 Starting MCP Test Server..."
    
    # Check if Python 3 is available
    if ! command -v python3 &> /dev/null; then
        print_message $RED "❌ Error: python3 is not installed or not in PATH"
        exit 1
    fi
    
    # Check if server is already running
    if is_server_running; then
        print_message $YELLOW "⚠️  MCP server is already running"
        return 0
    fi
    
    # Start server in background
    cd test-server
    python3 mcp_server.py --port $SERVER_PORT --host $SERVER_HOST > /tmp/mcp_server.log 2>&1 &
    local server_pid=$!
    echo $server_pid > "$SERVER_PID_FILE"
    cd ..
    
    # Wait for server to start
    print_message $YELLOW "⏳ Waiting for server to start..."
    local max_attempts=10
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$SERVER_URL" > /dev/null 2>&1; then
            print_message $GREEN "✅ MCP server started successfully on $SERVER_URL"
            print_message $BLUE "📋 Available tools:"
            print_message $BLUE "   - echo: Echoes back input text"
            print_message $BLUE "   - add_numbers: Adds two numbers together"
            print_message $BLUE "   - get_time: Returns current server time"
            print_message $BLUE "   - reverse_string: Reverses a string"
            return 0
        fi
        
        sleep 1
        attempt=$((attempt + 1))
    done
    
    print_message $RED "❌ Failed to start MCP server after $max_attempts attempts"
    print_message $RED "📄 Check server logs: tail -f /tmp/mcp_server.log"
    stop_server
    exit 1
}

# Function to stop MCP server
stop_server() {
    if is_server_running; then
        local pid=$(cat "$SERVER_PID_FILE")
        print_message $YELLOW "🛑 Stopping MCP server (PID: $pid)..."
        kill $pid 2>/dev/null || true
        rm -f "$SERVER_PID_FILE"
        print_message $GREEN "✅ MCP server stopped"
    fi
}

# Function to run the plugin
run_plugin() {
    print_message $BLUE "🔧 Building and running IntelliJ plugin..."
    
    # Check if gradlew exists
    if [ ! -f "./gradlew" ]; then
        print_message $RED "❌ Error: gradlew not found. Make sure you're in the project root directory."
        exit 1
    fi
    
    # Make gradlew executable
    chmod +x ./gradlew
    
    print_message $YELLOW "📦 Building plugin..."
    if ! ./gradlew compileKotlin; then
        print_message $RED "❌ Failed to build plugin"
        exit 1
    fi
    
    print_message $GREEN "✅ Plugin built successfully"
    print_message $BLUE "🚀 Starting IntelliJ IDEA with plugin..."
    print_message $YELLOW "💡 To test the plugin:"
    print_message $YELLOW "   1. Open 'MCP Inspector Lite' tool window"
    print_message $YELLOW "   2. Connect to: $SERVER_URL"
    print_message $YELLOW "   3. Browse and test the available tools"
    
    # Run the plugin (this will block until IDE is closed)
    ./gradlew runIde
}

# Function to cleanup on exit
cleanup() {
    print_message $YELLOW "🧹 Cleaning up..."
    stop_server
    print_message $GREEN "👋 Goodbye!"
}

# Set trap to cleanup on script exit
trap cleanup EXIT INT TERM

# Main execution
main() {
    print_message $GREEN "🎯 MCP Inspector Lite - Development Environment"
    print_message $GREEN "=============================================="
    
    # Check if we're in the right directory
    if [ ! -f "build.gradle.kts" ] || [ ! -d "test-server" ]; then
        print_message $RED "❌ Error: Please run this script from the mcp-plugin project root directory"
        exit 1
    fi
    
    # Start server
    start_server
    
    # Small delay to ensure server is fully ready
    sleep 2
    
    # Run plugin
    run_plugin
}

# Show help if requested
if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "MCP Inspector Lite - Development Environment"
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "This script starts the MCP test server and runs the IntelliJ plugin."
    echo "When the plugin/IDE is closed, the server is automatically stopped."
    echo ""
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo ""
    echo "Server will be available at: $SERVER_URL"
    echo "Server logs: /tmp/mcp_server.log"
    exit 0
fi

# Run main function
main
