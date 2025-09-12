#!/bin/bash

# MCP Inspector Lite - MCP Server Launcher
# This script starts the MCP test server with auto-reload capabilities

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
SERVER_PORT=3000
SERVER_HOST=localhost
SERVER_URL="http://${SERVER_HOST}:${SERVER_PORT}"
SERVER_PID_FILE="/tmp/mcp_server.pid"
SERVER_LOG_FILE="/tmp/mcp_server.log"
WATCH_MODE=false

# Function to print colored messages
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to print usage
show_help() {
    echo "MCP Inspector Lite - MCP Server Launcher"
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -w, --watch        Enable auto-reload when server files change"
    echo "  -p, --port PORT    Set server port (default: 3000)"
    echo "  -h, --host HOST    Set server host (default: localhost)"
    echo "  --help             Show this help message"
    echo "  --stop             Stop running MCP server"
    echo "  --status           Check server status"
    echo "  --logs             Show server logs"
    echo ""
    echo "Examples:"
    echo "  $0                 Start server normally"
    echo "  $0 --watch         Start server with auto-reload"
    echo "  $0 --stop          Stop running server"
    echo "  $0 --logs          Show server logs"
    exit 0
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

# Function to stop MCP server
stop_server() {
    if is_server_running; then
        local pid=$(cat "$SERVER_PID_FILE")
        print_message $YELLOW "🛑 Stopping MCP server (PID: $pid)..."
        kill $pid 2>/dev/null || true
        rm -f "$SERVER_PID_FILE"
        print_message $GREEN "✅ MCP server stopped"
        return 0
    else
        print_message $YELLOW "⚠️  MCP server is not running"
        return 1
    fi
}

# Function to show server status
show_status() {
    if is_server_running; then
        local pid=$(cat "$SERVER_PID_FILE")
        print_message $GREEN "✅ MCP server is running (PID: $pid)"
        print_message $BLUE "🌐 Server URL: $SERVER_URL"
        print_message $BLUE "📄 Logs: $SERVER_LOG_FILE"
        
        # Test server connectivity
        if curl -s "$SERVER_URL" > /dev/null 2>&1; then
            print_message $GREEN "🔗 Server is responding"
        else
            print_message $RED "❌ Server is not responding"
        fi
    else
        print_message $RED "❌ MCP server is not running"
    fi
}

# Function to show server logs
show_logs() {
    if [ -f "$SERVER_LOG_FILE" ]; then
        print_message $BLUE "📄 Server logs (last 50 lines):"
        echo ""
        tail -n 50 "$SERVER_LOG_FILE"
        echo ""
        print_message $CYAN "💡 To follow logs in real-time: tail -f $SERVER_LOG_FILE"
    else
        print_message $YELLOW "⚠️  No log file found at $SERVER_LOG_FILE"
    fi
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
        show_status
        return 0
    fi
    
    # Check if test-server directory exists
    if [ ! -d "test-server" ]; then
        print_message $RED "❌ Error: test-server directory not found"
        print_message $RED "   Make sure you're running this script from the project root"
        exit 1
    fi
    
    # Install dependencies if requirements.txt exists
    if [ -f "test-server/requirements.txt" ]; then
        print_message $YELLOW "📦 Installing Python dependencies..."
        cd test-server
        python3 -m pip install -r requirements.txt > /dev/null 2>&1 || true
        cd ..
    fi
    
    # Start server in background
    cd test-server
    print_message $YELLOW "⏳ Starting server on $SERVER_URL..."
    python3 mcp_server.py --port $SERVER_PORT --host $SERVER_HOST > "$SERVER_LOG_FILE" 2>&1 &
    local server_pid=$!
    echo $server_pid > "$SERVER_PID_FILE"
    cd ..
    
    # Wait for server to start
    local max_attempts=10
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$SERVER_URL" > /dev/null 2>&1; then
            print_message $GREEN "✅ MCP server started successfully!"
            print_message $BLUE "🌐 Server URL: $SERVER_URL"
            print_message $BLUE "📋 Available tools (7):"
            print_message $BLUE "   - echo: Echoes back input text"
            print_message $BLUE "   - add_numbers: Adds two numbers together"
            print_message $BLUE "   - get_time: Returns current server time"
            print_message $BLUE "   - reverse_string: Reverses a string"
            print_message $BLUE "   - server_info: Returns server information"
            print_message $BLUE "   - calculate: Performs mathematical calculations"
            print_message $BLUE "   - generate_uuid: Generates random UUIDs"
            print_message $CYAN "📄 Server logs: $SERVER_LOG_FILE"
            return 0
        fi
        
        sleep 1
        attempt=$((attempt + 1))
    done
    
    print_message $RED "❌ Failed to start MCP server after $max_attempts attempts"
    print_message $RED "📄 Check server logs:"
    show_logs
    stop_server
    exit 1
}

# Function to start server with file watching
start_server_with_watch() {
    # Check if fswatch is available
    if ! command -v fswatch &> /dev/null; then
        print_message $YELLOW "⚠️  fswatch not found. Installing via Homebrew..."
        if command -v brew &> /dev/null; then
            brew install fswatch
        else
            print_message $RED "❌ Homebrew not found. Please install fswatch manually:"
            print_message $RED "   brew install fswatch"
            print_message $RED "   or use your package manager"
            exit 1
        fi
    fi
    
    print_message $CYAN "👁️  Starting MCP server with auto-reload..."
    
    # Start server initially
    start_server
    
    print_message $CYAN "🔍 Watching for file changes in test-server/..."
    print_message $YELLOW "💡 Edit server files and see changes automatically!"
    print_message $YELLOW "   Press Ctrl+C to stop"
    
    # Watch for changes and restart server
    fswatch -o test-server/ | while read f; do
        print_message $YELLOW "🔄 File changes detected, restarting server..."
        stop_server
        sleep 1
        start_server
        print_message $GREEN "🔄 Server restarted and ready!"
    done
}

# Function to cleanup on exit
cleanup() {
    if [ "$WATCH_MODE" = true ]; then
        print_message $YELLOW "🧹 Cleaning up..."
        stop_server
    fi
}

# Set trap to cleanup on script exit
trap cleanup EXIT INT TERM

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -w|--watch)
            WATCH_MODE=true
            shift
            ;;
        -p|--port)
            SERVER_PORT="$2"
            SERVER_URL="http://${SERVER_HOST}:${SERVER_PORT}"
            shift 2
            ;;
        -h|--host)
            SERVER_HOST="$2"
            SERVER_URL="http://${SERVER_HOST}:${SERVER_PORT}"
            shift 2
            ;;
        --help)
            show_help
            ;;
        --stop)
            stop_server
            exit $?
            ;;
        --status)
            show_status
            exit 0
            ;;
        --logs)
            show_logs
            exit 0
            ;;
        *)
            print_message $RED "❌ Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Main execution
main() {
    print_message $GREEN "🎯 MCP Inspector Lite - MCP Server"
    print_message $GREEN "=================================="
    
    # Check if we're in the right directory
    if [ ! -f "build.gradle.kts" ] || [ ! -d "test-server" ]; then
        print_message $RED "❌ Error: Please run this script from the mcp-plugin project root directory"
        exit 1
    fi
    
    if [ "$WATCH_MODE" = true ]; then
        start_server_with_watch
    else
        start_server
        print_message $CYAN "💡 Server is running. Use '$0 --stop' to stop it"
        print_message $CYAN "💡 Use '$0 --watch' for auto-reload during development"
        print_message $CYAN "💡 Use '$0 --logs' to view server logs"
    fi
}

# Run main function
main
