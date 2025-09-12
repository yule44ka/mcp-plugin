#!/usr/bin/env python3
"""
Test script to validate number input validation for add_numbers and calculate tools
"""

import json
import requests
import sys

def test_tool(base_url, tool_name, arguments, expected_success=True):
    """Test a tool with given arguments"""
    request_data = {
        "jsonrpc": "2.0",
        "id": f"test_{tool_name}",
        "method": "tools/call",
        "params": {
            "name": tool_name,
            "arguments": arguments
        }
    }
    
    try:
        response = requests.post(base_url, json=request_data)
        response.raise_for_status()
        result = response.json()
        
        # Check for JSON-RPC error (proper error handling)
        if 'error' in result:
            error_message = result['error']['message']
            if expected_success:
                print(f"❌ {tool_name} with {arguments}: Unexpected error - {error_message}")
                return False
            else:
                print(f"✅ {tool_name} with {arguments}: Correctly rejected - {error_message}")
                return True
        
        # Check for success result
        if 'result' in result:
            content = result.get('result', {}).get('content', [])
            if content:
                response_text = content[0].get('text', 'No text')
                if expected_success:
                    print(f"✅ {tool_name} with {arguments}: {response_text}")
                    return True
                else:
                    print(f"❌ {tool_name} with {arguments}: Should have failed but got success - {response_text}")
                    return False
            else:
                print(f"❌ {tool_name} with {arguments}: No content in success response")
                return False
        else:
            print(f"❌ {tool_name} with {arguments}: Invalid response format")
            return False
            
    except Exception as e:
        print(f"❌ {tool_name} with {arguments}: Request failed - {e}")
        return False

def main():
    base_url = "http://localhost:3000"
    
    print("🧪 Testing MCP Tool Input Validation")
    print("====================================")
    
    # Check if server is running
    try:
        response = requests.get(base_url)
    except:
        print("❌ MCP server is not running. Please start it first:")
        print("   ./dev-start.sh server")
        sys.exit(1)
    
    print("✅ MCP server is running\n")
    
    all_tests_passed = True
    
    # Test add_numbers tool
    print("🔢 Testing add_numbers tool:")
    print("-" * 30)
    
    # Valid cases
    test_cases = [
        # Valid numbers
        ({"a": 5, "b": 3}, True, "Valid integers"),
        ({"a": 2.5, "b": 1.5}, True, "Valid floats"),
        ({"a": -10, "b": 15}, True, "Negative and positive"),
        ({"a": 0, "b": 0}, True, "Zeros"),
        
        # Invalid cases - strings that look like numbers
        ({"a": "5", "b": "3"}, False, "String numbers"),
        ({"a": "hello", "b": "world"}, False, "Text strings"),
        ({"a": 5, "b": "3"}, False, "Mixed number and string"),
        ({"a": "abc", "b": 123}, False, "Mixed string and number"),
        
        # Missing parameters
        ({"a": 5}, False, "Missing parameter b"),
        ({"b": 3}, False, "Missing parameter a"),
        ({}, False, "Missing both parameters"),
        
        # Other types
        ({"a": [1, 2], "b": 3}, False, "Array as parameter"),
        ({"a": {"num": 5}, "b": 3}, False, "Object as parameter"),
        ({"a": None, "b": 3}, False, "Null as parameter"),
        ({"a": True, "b": False}, False, "Boolean as parameter"),
    ]
    
    for args, should_succeed, description in test_cases:
        print(f"  {description}: ", end="")
        success = test_tool(base_url, "add_numbers", args, should_succeed)
        if not success:
            all_tests_passed = False
    
    print("\n🧮 Testing calculate tool:")
    print("-" * 25)
    
    # Test calculate tool
    calc_cases = [
        # Valid expressions
        ("2 + 3", True, "Simple addition"),
        ("10 - 4", True, "Simple subtraction"),
        ("6 * 7", True, "Simple multiplication"),
        ("15 / 3", True, "Simple division"),
        ("2 ** 3", True, "Exponentiation"),
        ("(2 + 3) * 4", True, "Expression with parentheses"),
        ("2.5 + 1.5", True, "Decimal numbers"),
        
        # Invalid expressions
        ("hello + world", False, "Text in expression"),
        ("2 + abc", False, "Mixed numbers and text"),
        ("1 + ц", False, "Cyrillic characters in expression"),
        ("привет + мир", False, "Full Cyrillic expression"),
        ("import os", False, "Import statement"),
        ("print('hello')", False, "Function call"),
        ("", False, "Empty expression"),
        ("2 +", False, "Incomplete expression"),
        ("/ 5", False, "Invalid syntax"),
    ]
    
    for expr, should_succeed, description in calc_cases:
        print(f"  {description}: ", end="")
        success = test_tool(base_url, "calculate", {"expression": expr}, should_succeed)
        if not success:
            all_tests_passed = False
    
    print("\n" + "=" * 50)
    if all_tests_passed:
        print("🎉 All validation tests passed!")
        print("✅ add_numbers correctly validates number inputs")
        print("✅ calculate correctly handles invalid expressions")
    else:
        print("❌ Some tests failed!")
        print("🔧 Check the validation logic in the MCP server")
    
    return all_tests_passed

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
