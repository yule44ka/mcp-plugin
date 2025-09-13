#!/usr/bin/env python3
"""
Simple test client to verify our JSON-RPC format is correct
"""
import asyncio
import json
import aiohttp

async def test_mcp_connection():
    # First, get the SSE endpoint
    async with aiohttp.ClientSession() as session:
        print("Connecting to SSE endpoint...")
        async with session.get('http://localhost:8050/sse') as resp:
            if resp.status == 200:
                # Read the first few lines to get the session info
                async for line in resp.content:
                    line_str = line.decode('utf-8').strip()
                    print(f"SSE: {line_str}")
                    
                    if line_str.startswith('data: /messages/'):
                        endpoint = line_str[6:]  # Remove 'data: '
                        print(f"Got messages endpoint: {endpoint}")
                        
                        # Now test sending a proper JSON-RPC request
                        test_request = {
                            "jsonrpc": "2.0",
                            "id": "test_123",
                            "method": "initialize",
                            "params": {
                                "protocolVersion": "2024-11-05",
                                "capabilities": {},
                                "clientInfo": {
                                    "name": "Test Client",
                                    "version": "1.0.0"
                                }
                            }
                        }
                        
                        print(f"Sending request: {json.dumps(test_request, indent=2)}")
                        
                        # Send the request
                        async with session.post(f'http://localhost:8050{endpoint}', 
                                              json=test_request) as post_resp:
                            print(f"Response status: {post_resp.status}")
                            if post_resp.status == 200:
                                response_text = await post_resp.text()
                                print(f"Response: {response_text}")
                            else:
                                error_text = await post_resp.text()
                                print(f"Error: {error_text}")
                        
                        break
                    
                    # Stop after getting a few lines
                    if line_str.startswith(': ping'):
                        break
            else:
                print(f"Failed to connect to SSE: {resp.status}")

if __name__ == "__main__":
    asyncio.run(test_mcp_connection())
