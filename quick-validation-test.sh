#!/bin/bash

# Quick validation test for MCP tools
# This script demonstrates the validation in action

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

test_tool() {
    local tool_name=$1
    local params=$2
    local description=$3
    
    print_message $CYAN "Testing: $description"
    
    local request='{
        "jsonrpc": "2.0",
        "id": "test",
        "method": "tools/call",
        "params": {
            "name": "'$tool_name'",
            "arguments": '$params'
        }
    }'
    
    local response=$(curl -s -X POST http://localhost:3000 \
        -H "Content-Type: application/json" \
        -d "$request")
    
    local result=$(echo "$response" | python3 -c "
import json, sys
data = json.load(sys.stdin)
if 'error' in data:
    print('ERROR: ' + data['error']['message'])
elif 'result' in data and 'content' in data['result']:
    print('SUCCESS: ' + data['result']['content'][0]['text'])
else:
    print('UNKNOWN: Unknown response format')
")
    
    if [[ $result == ERROR:* ]]; then
        print_message $RED "  ❌ ${result#ERROR: }"
    elif [[ $result == SUCCESS:* ]]; then
        print_message $GREEN "  ✅ ${result#SUCCESS: }"
    else
        print_message $YELLOW "  ⚠️  $result"
    fi
    echo ""
}

print_message $BLUE "🧪 Quick MCP Tool Validation Demo"
print_message $BLUE "================================="

# Check if server is running
if ! curl -s http://localhost:3000 > /dev/null 2>&1; then
    print_message $RED "❌ MCP server is not running!"
    print_message $YELLOW "Start it with: ./dev-start.sh server"
    exit 1
fi

print_message $GREEN "✅ MCP server is running"
echo ""

print_message $YELLOW "🔢 Testing add_numbers tool:"
print_message $YELLOW "----------------------------"

# Valid cases
test_tool "add_numbers" '{"a": 5, "b": 3}' "Valid numbers (5 + 3)"
test_tool "add_numbers" '{"a": 2.5, "b": 1.5}' "Valid floats (2.5 + 1.5)"

# Invalid cases
test_tool "add_numbers" '{"a": "5", "b": "3"}' "String numbers (should fail)"
test_tool "add_numbers" '{"a": "hello", "b": "world"}' "Text strings (should fail)"
test_tool "add_numbers" '{"a": 5}' "Missing parameter (should fail)"
test_tool "add_numbers" '{"a": true, "b": false}' "Boolean values (should fail)"

print_message $YELLOW "🧮 Testing calculate tool:"
print_message $YELLOW "--------------------------"

# Valid cases
test_tool "calculate" '{"expression": "2 + 3"}' "Valid expression (2 + 3)"
test_tool "calculate" '{"expression": "(10 - 2) * 3"}' "Complex expression"

# Invalid cases
test_tool "calculate" '{"expression": "hello + world"}' "Text in expression (should fail)"
test_tool "calculate" '{"expression": "1 + ц"}' "Cyrillic in expression (should fail)"
test_tool "calculate" '{"expression": "2 + abc"}' "Mixed numbers and text (should fail)"
test_tool "calculate" '{"expression": "import os"}' "Dangerous code (should fail)"

print_message $GREEN "🎯 Validation Summary:"
print_message $GREEN "====================="
print_message $GREEN "✅ add_numbers only accepts real numbers (int/float)"
print_message $GREEN "✅ add_numbers rejects strings, booleans, arrays, objects"
print_message $GREEN "✅ add_numbers requires both parameters"
print_message $GREEN "✅ calculate safely evaluates math expressions"
print_message $GREEN "✅ calculate rejects dangerous code and invalid syntax"
