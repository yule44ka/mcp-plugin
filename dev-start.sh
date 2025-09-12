#!/bin/bash

# MCP Inspector Lite - Development Quick Start
# This script provides easy commands to start development environment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to print colored messages
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to print usage
show_help() {
    echo "MCP Inspector Lite - Development Quick Start"
    echo ""
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  server         Start MCP server with auto-reload (default behavior)"
    echo "  server-no-watch Start MCP server without auto-reload"
    echo "  inspector      Start IntelliJ plugin only"
    echo "  both           Start both server and inspector"
    echo "  stop           Stop running MCP server"
    echo "  status         Check server status"
    echo "  logs           Show server logs"
    echo "  clean          Clean build and start inspector"
    echo ""
    echo "Examples:"
    echo "  $0 server          # Start server with auto-reload (default)"
    echo "  $0 server-no-watch # Start server without auto-reload"
    echo "  $0 inspector       # Start inspector (in another terminal)"
    echo "  $0 both            # Start server, then inspector"
    echo "  $0 stop            # Stop the server"
    exit 0
}

# Check if we're in the right directory
check_directory() {
    if [ ! -f "build.gradle.kts" ] || [ ! -d "test-server" ]; then
        print_message $RED "❌ Error: Please run this script from the mcp-plugin project root directory"
        exit 1
    fi
}

# Main execution
case "${1:-help}" in
    "server")
        check_directory
        print_message $BLUE "🚀 Starting MCP server with auto-reload..."
        ./start-mcp-server.sh
        ;;
    "server-no-watch")
        check_directory
        print_message $BLUE "🚀 Starting MCP server without auto-reload..."
        ./start-mcp-server.sh --no-watch
        ;;
    "inspector")
        check_directory
        print_message $BLUE "🚀 Starting IntelliJ plugin..."
        ./start-inspector.sh --check-server
        ;;
    "both")
        check_directory
        print_message $BLUE "🚀 Starting both server (with auto-reload) and inspector"
        print_message $CYAN "💡 For development, consider using separate terminals:"
        print_message $CYAN "   Terminal 1: $0 server"
        print_message $CYAN "   Terminal 2: $0 inspector"
        echo ""
        ./run-plugin-with-server.sh
        ;;
    "stop")
        check_directory
        ./start-mcp-server.sh --stop
        ;;
    "status")
        check_directory
        ./start-mcp-server.sh --status
        ;;
    "logs")
        check_directory
        ./start-mcp-server.sh --logs
        ;;
    "clean")
        check_directory
        print_message $BLUE "🧹 Clean build and start inspector..."
        ./start-inspector.sh --clean --check-server
        ;;
    "help"|"--help"|"-h"|"")
        show_help
        ;;
    *)
        print_message $RED "❌ Unknown command: $1"
        echo ""
        show_help
        ;;
esac
