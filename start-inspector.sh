#!/bin/bash

# MCP Inspector Lite - Inspector (IntelliJ Plugin) Launcher
# This script builds and runs the IntelliJ plugin for MCP inspection

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
DEFAULT_SERVER_URL="http://localhost:3000"
CLEAN_BUILD=false
DEBUG_MODE=false

# Function to print colored messages
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to print usage
show_help() {
    echo "MCP Inspector Lite - Inspector (IntelliJ Plugin) Launcher"
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -c, --clean        Clean build before running"
    echo "  -d, --debug        Enable debug mode"
    echo "  -s, --server URL   Set default MCP server URL (default: http://localhost:3000)"
    echo "  --help             Show this help message"
    echo "  --build-only       Only build the plugin, don't run IntelliJ"
    echo "  --check-server     Check if MCP server is running before starting"
    echo ""
    echo "Examples:"
    echo "  $0                           Start inspector normally"
    echo "  $0 --clean                   Clean build and start"
    echo "  $0 --check-server            Check server status first"
    echo "  $0 --server http://localhost:8080  Use custom server URL"
    exit 0
}

# Function to check if MCP server is running
check_server() {
    local server_url=$1
    print_message $BLUE "🔍 Checking MCP server at $server_url..."
    
    if curl -s "$server_url" > /dev/null 2>&1; then
        print_message $GREEN "✅ MCP server is running and responding"
        return 0
    else
        print_message $YELLOW "⚠️  MCP server is not responding at $server_url"
        print_message $CYAN "💡 You can start the server with: ./start-mcp-server.sh"
        print_message $CYAN "💡 Or start with auto-reload: ./start-mcp-server.sh --watch"
        return 1
    fi
}

# Function to clean build
clean_build() {
    print_message $YELLOW "🧹 Cleaning previous build..."
    if ! ./gradlew clean; then
        print_message $RED "❌ Failed to clean build"
        exit 1
    fi
    print_message $GREEN "✅ Build cleaned successfully"
}

# Function to build plugin
build_plugin() {
    print_message $BLUE "🔧 Building IntelliJ plugin..."
    
    # Make gradlew executable
    chmod +x ./gradlew
    
    local gradle_tasks="compileKotlin"
    if [ "$DEBUG_MODE" = true ]; then
        gradle_tasks="$gradle_tasks --debug"
        print_message $CYAN "🐛 Debug mode enabled"
    fi
    
    print_message $YELLOW "📦 Compiling Kotlin sources..."
    if ! ./gradlew $gradle_tasks; then
        print_message $RED "❌ Failed to build plugin"
        print_message $RED "💡 Check the error messages above for details"
        exit 1
    fi
    
    print_message $GREEN "✅ Plugin built successfully"
}

# Function to run IntelliJ with plugin
run_intellij() {
    print_message $BLUE "🚀 Starting IntelliJ IDEA with MCP Inspector plugin..."
    print_message $YELLOW "💡 Plugin usage instructions:"
    print_message $YELLOW "   1. Wait for IntelliJ IDEA to fully load"
    print_message $YELLOW "   2. Look for 'MCP Inspector Lite' in the tool windows"
    print_message $YELLOW "   3. Open the tool window (usually at the bottom or sides)"
    print_message $YELLOW "   4. Connect to MCP server: $DEFAULT_SERVER_URL"
    print_message $YELLOW "   5. Browse and test available tools"
    print_message $CYAN ""
    print_message $CYAN "🔧 Development tips:"
    print_message $CYAN "   - Keep the MCP server running in a separate terminal"
    print_message $CYAN "   - Use './start-mcp-server.sh --watch' for auto-reload"
    print_message $CYAN "   - Check server logs with './start-mcp-server.sh --logs'"
    print_message $CYAN "   - Server status: './start-mcp-server.sh --status'"
    echo ""
    
    # Run the plugin (this will block until IDE is closed)
    if [ "$DEBUG_MODE" = true ]; then
        print_message $CYAN "🐛 Running in debug mode..."
        ./gradlew runIde --debug-jvm
    else
        ./gradlew runIde
    fi
}

# Function to check prerequisites
check_prerequisites() {
    print_message $BLUE "🔍 Checking prerequisites..."
    
    # Check if we're in the right directory
    if [ ! -f "build.gradle.kts" ]; then
        print_message $RED "❌ Error: build.gradle.kts not found"
        print_message $RED "   Please run this script from the mcp-plugin project root directory"
        exit 1
    fi
    
    # Check if gradlew exists
    if [ ! -f "./gradlew" ]; then
        print_message $RED "❌ Error: gradlew not found"
        print_message $RED "   Make sure you're in the project root directory"
        exit 1
    fi
    
    # Check Java version
    if ! command -v java &> /dev/null; then
        print_message $RED "❌ Error: Java is not installed or not in PATH"
        print_message $RED "   IntelliJ plugin development requires Java 17 or later"
        exit 1
    fi
    
    local java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt 17 ] 2>/dev/null; then
        print_message $YELLOW "⚠️  Warning: Java version might be too old (detected: $java_version)"
        print_message $YELLOW "   IntelliJ plugin development works best with Java 17+"
    else
        print_message $GREEN "✅ Java version OK"
    fi
    
    print_message $GREEN "✅ Prerequisites check passed"
}

# Parse command line arguments
BUILD_ONLY=false
CHECK_SERVER_FIRST=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--clean)
            CLEAN_BUILD=true
            shift
            ;;
        -d|--debug)
            DEBUG_MODE=true
            shift
            ;;
        -s|--server)
            DEFAULT_SERVER_URL="$2"
            shift 2
            ;;
        --help)
            show_help
            ;;
        --build-only)
            BUILD_ONLY=true
            shift
            ;;
        --check-server)
            CHECK_SERVER_FIRST=true
            shift
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
    print_message $GREEN "🎯 MCP Inspector Lite - Inspector Launcher"
    print_message $GREEN "========================================="
    
    # Check prerequisites
    check_prerequisites
    
    # Check server if requested
    if [ "$CHECK_SERVER_FIRST" = true ]; then
        if ! check_server "$DEFAULT_SERVER_URL"; then
            print_message $YELLOW "⚠️  Continue anyway? (y/N)"
            read -r response
            if [[ ! "$response" =~ ^[Yy]$ ]]; then
                print_message $YELLOW "👋 Cancelled by user"
                exit 0
            fi
        fi
    fi
    
    # Clean build if requested
    if [ "$CLEAN_BUILD" = true ]; then
        clean_build
    fi
    
    # Build plugin
    build_plugin
    
    # Run IntelliJ or just build
    if [ "$BUILD_ONLY" = true ]; then
        print_message $GREEN "✅ Build completed successfully"
        print_message $CYAN "💡 To run the plugin: ./gradlew runIde"
    else
        run_intellij
    fi
}

# Run main function
main
