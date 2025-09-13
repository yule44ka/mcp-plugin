#!/bin/bash

# Development Environment Startup Script
# This script starts both the MCP server with auto-reload and the IntelliJ plugin

set -e  # Exit on any error

echo "🚀 MCP Plugin Development Environment"
echo "====================================="
echo ""
echo "This script will start:"
echo "  1. 🔄 MCP Server with auto-reload (port 8050)"
echo "  2. 🔌 IntelliJ IDEA with MCP Plugin"
echo ""

# Function to cleanup background processes
cleanup() {
    echo ""
    echo "🛑 Shutting down development environment..."
    
    # Kill server if running
    if [ ! -z "$SERVER_PID" ]; then
        echo "🛑 Stopping MCP server..."
        kill $SERVER_PID 2>/dev/null || true
        wait $SERVER_PID 2>/dev/null || true
    fi
    
    # Kill plugin if running
    if [ ! -z "$PLUGIN_PID" ]; then
        echo "🛑 Stopping IntelliJ plugin..."
        kill $PLUGIN_PID 2>/dev/null || true
        wait $PLUGIN_PID 2>/dev/null || true
    fi
    
    echo "👋 Development environment stopped."
    exit 0
}

# Set up signal handlers
trap cleanup SIGINT SIGTERM

# Check if we're in the right directory
if [ ! -f "build.gradle.kts" ]; then
    echo "❌ Error: build.gradle.kts not found. Please run this script from the project root."
    exit 1
fi

# Check dependencies
echo "📦 Checking dependencies..."

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not in PATH"
    exit 1
fi

# Check Python
if ! command -v python &> /dev/null; then
    echo "❌ Error: Python is not installed or not in PATH"
    exit 1
fi

# Check Gradle wrapper
if [ ! -f "./gradlew" ]; then
    echo "❌ Error: Gradle wrapper not found"
    exit 1
fi

echo "✅ All dependencies available!"

# Ask user what to start
echo ""
echo "Choose startup mode:"
echo "  1) Start both server and plugin (recommended)"
echo "  2) Start only MCP server with auto-reload"
echo "  3) Start only IntelliJ plugin"
echo ""
read -p "Enter your choice (1-3): " choice

case $choice in
    1)
        echo ""
        echo "🔄 Starting MCP server with auto-reload..."
        ./run-server-watch.sh &
        SERVER_PID=$!
        
        # Wait a bit for server to start
        sleep 3
        
        echo ""
        echo "🔌 Starting IntelliJ IDEA with MCP plugin..."
        ./run-plugin.sh &
        PLUGIN_PID=$!
        
        echo ""
        echo "✅ Both services started!"
        echo "📋 Development Environment Status:"
        echo "   🔄 MCP Server: Running on http://localhost:8050 (PID: $SERVER_PID)"
        echo "   🔌 IntelliJ Plugin: Starting... (PID: $PLUGIN_PID)"
        echo ""
        echo "💡 Usage Tips:"
        echo "   - Edit files in simple-server-setup/ to see auto-reload"
        echo "   - Test the plugin in IntelliJ: Tools > MCP Inspector"
        echo "   - Press Ctrl+C to stop both services"
        echo ""
        
        # Wait for both processes
        wait
        ;;
    2)
        echo ""
        echo "🔄 Starting only MCP server with auto-reload..."
        exec ./run-server-watch.sh
        ;;
    3)
        echo ""
        echo "🔌 Starting only IntelliJ plugin..."
        exec ./run-plugin.sh
        ;;
    *)
        echo "❌ Invalid choice. Please run the script again and choose 1, 2, or 3."
        exit 1
        ;;
esac
