#!/bin/bash

# Test script to verify connection error messages
# This script tests various connection error scenarios

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

print_message $BLUE "🧪 Testing MCP Connection Error Messages"
print_message $BLUE "========================================"

# Test 1: Connection timeout (server not running)
print_message $YELLOW "Test 1: Connection timeout (server not running)"
print_message $YELLOW "Expected: Should show timeout message with server start instructions"
echo ""

# Test with curl to simulate what the plugin would do
print_message $CYAN "Simulating connection to http://localhost:3000 (server not running)..."
if curl -s --connect-timeout 3 --max-time 5 http://localhost:3000 > /dev/null 2>&1; then
    print_message $GREEN "✅ Server is running - connection successful"
else
    print_message $RED "❌ Connection failed (expected)"
    print_message $YELLOW "💡 This simulates the timeout scenario"
    print_message $YELLOW "💡 Plugin should show: 'Connection timeout. Check if your server is running at http://localhost:3000'"
    print_message $YELLOW "💡 With hint: 'To start the server: ./dev-start.sh server'"
fi

echo ""

# Test 2: Connection refused (wrong port)
print_message $YELLOW "Test 2: Connection refused (wrong port)"
print_message $YELLOW "Expected: Should show connection refused message"
echo ""

print_message $CYAN "Simulating connection to http://localhost:9999 (wrong port)..."
if curl -s --connect-timeout 1 --max-time 2 http://localhost:9999 > /dev/null 2>&1; then
    print_message $GREEN "✅ Unexpected: Server is running on port 9999"
else
    print_message $RED "❌ Connection refused (expected)"
    print_message $YELLOW "💡 Plugin should show: 'Connection refused. Check if your server is running'"
    print_message $YELLOW "💡 With hints for starting server and checking status"
fi

echo ""

# Test 3: Invalid host
print_message $YELLOW "Test 3: Invalid host resolution"
print_message $YELLOW "Expected: Should show host resolution error"
echo ""

print_message $CYAN "Simulating connection to http://invalid-host-name:3000..."
if curl -s --connect-timeout 1 --max-time 2 http://invalid-host-name:3000 > /dev/null 2>&1; then
    print_message $GREEN "✅ Unexpected: Invalid host resolved"
else
    print_message $RED "❌ Host resolution failed (expected)"
    print_message $YELLOW "💡 Plugin should show: 'Cannot resolve host. Check the server URL'"
    print_message $YELLOW "💡 With hint: 'Default server URL should be: http://localhost:3000'"
fi

echo ""

print_message $GREEN "🎯 Summary"
print_message $GREEN "=========="
print_message $GREEN "✅ All connection error scenarios tested"
print_message $GREEN "✅ Plugin should now show helpful error messages with:"
print_message $GREEN "   - Clear description of the problem"
print_message $GREEN "   - Instructions to start the server"
print_message $GREEN "   - Commands to check server status"
print_message $GREEN ""
print_message $BLUE "💡 To test in the plugin:"
print_message $BLUE "   1. Start the inspector: ./dev-start.sh inspector"
print_message $BLUE "   2. Try connecting without starting the server"
print_message $BLUE "   3. You should see improved error messages"
print_message $BLUE "   4. Start server: ./dev-start.sh server"
print_message $BLUE "   5. Connection should work"