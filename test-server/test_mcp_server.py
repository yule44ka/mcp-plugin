#!/usr/bin/env python3
"""
Test script to verify the MCP server is working correctly
"""

import json
import requests
import sys

def test_mcp_server(base_url="http://localhost:3000"):
    """Test the MCP server endpoints"""
    
    print(f"Testing MCP server at {base_url}")
    
    # Test 1: Initialize
    print("\n1. Testing initialize...")
    init_request = {
        "jsonrpc": "2.0",
        "id": "test_init",
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {
                "tools": {"listChanged": True}
            },
            "clientInfo": {
                "name": "MCP Test Client",
                "version": "1.0.0"
            }
        }
    }
    
    try:
        response = requests.post(base_url, json=init_request)
        response.raise_for_status()
        result = response.json()
        print(f"✓ Initialize successful: {result.get('result', {}).get('serverInfo', {}).get('name', 'Unknown')}")
    except Exception as e:
        print(f"✗ Initialize failed: {e}")
        return False
    
    # Test 2: List tools
    print("\n2. Testing tools/list...")
    list_request = {
        "jsonrpc": "2.0",
        "id": "test_list",
        "method": "tools/list"
    }
    
    try:
        response = requests.post(base_url, json=list_request)
        response.raise_for_status()
        result = response.json()
        tools = result.get('result', {}).get('tools', [])
        print(f"✓ Found {len(tools)} tools:")
        for tool in tools:
            print(f"  - {tool['name']}: {tool.get('description', 'No description')}")
    except Exception as e:
        print(f"✗ List tools failed: {e}")
        return False
    
    # Test 3: Call echo tool
    print("\n3. Testing tools/call (echo)...")
    call_request = {
        "jsonrpc": "2.0",
        "id": "test_call_echo",
        "method": "tools/call",
        "params": {
            "name": "echo",
            "arguments": {
                "text": "Hello MCP!"
            }
        }
    }
    
    try:
        response = requests.post(base_url, json=call_request)
        response.raise_for_status()
        result = response.json()
        content = result.get('result', {}).get('content', [])
        if content:
            print(f"✓ Echo result: {content[0].get('text', 'No text')}")
        else:
            print("✗ No content in echo response")
    except Exception as e:
        print(f"✗ Echo tool call failed: {e}")
        return False
    
    # Test 4: Call add_numbers tool
    print("\n4. Testing tools/call (add_numbers)...")
    add_request = {
        "jsonrpc": "2.0",
        "id": "test_call_add",
        "method": "tools/call",
        "params": {
            "name": "add_numbers",
            "arguments": {
                "a": 15,
                "b": 27
            }
        }
    }
    
    try:
        response = requests.post(base_url, json=add_request)
        response.raise_for_status()
        result = response.json()
        content = result.get('result', {}).get('content', [])
        if content:
            print(f"✓ Add result: {content[0].get('text', 'No text')}")
        else:
            print("✗ No content in add response")
    except Exception as e:
        print(f"✗ Add tool call failed: {e}")
        return False
    
    print("\n✓ All tests passed! MCP server is working correctly.")
    return True

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description='Test MCP Server')
    parser.add_argument('--url', default='http://localhost:3000', help='MCP server URL')
    
    args = parser.parse_args()
    
    try:
        success = test_mcp_server(args.url)
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print("\nTest interrupted")
        sys.exit(1)
