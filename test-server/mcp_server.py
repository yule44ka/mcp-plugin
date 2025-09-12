#!/usr/bin/env python3
"""
Simple MCP (Model Context Protocol) test server
Provides a few example tools for testing the MCP Inspector Lite plugin
"""

import json
import uuid
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Dict, Any, List
import argparse
import sys

class MCPTool:
    def __init__(self, name: str, description: str, input_schema: Dict[str, Any] = None):
        self.name = name
        self.description = description
        self.input_schema = input_schema or {}
    
    def to_dict(self):
        return {
            "name": self.name,
            "description": self.description,
            "inputSchema": self.input_schema
        }

class MCPServer:
    def __init__(self):
        self.tools = [
            MCPTool(
                name="echo",
                description="Echoes back the input text",
                input_schema={
                    "type": "object",
                    "properties": {
                        "text": {
                            "type": "string",
                            "description": "Text to echo back"
                        }
                    },
                    "required": ["text"]
                }
            ),
            MCPTool(
                name="add_numbers",
                description="Adds two numbers together",
                input_schema={
                    "type": "object",
                    "properties": {
                        "a": {
                            "type": "number",
                            "description": "First number"
                        },
                        "b": {
                            "type": "number",
                            "description": "Second number"
                        }
                    },
                    "required": ["a", "b"]
                }
            ),
            MCPTool(
                name="get_time",
                description="Returns the current server time",
                input_schema={
                    "type": "object",
                    "properties": {}
                }
            ),
            MCPTool(
                name="reverse_string",
                description="Reverses a string",
                input_schema={
                    "type": "object",
                    "properties": {
                        "text": {
                            "type": "string",
                            "description": "Text to reverse"
                        }
                    },
                    "required": ["text"]
                }
            ),
            MCPTool(
                name="server_info",
                description="Returns information about the MCP server",
                input_schema={
                    "type": "object",
                    "properties": {}
                }
            ),
            MCPTool(
                name="calculate",
                description="Performs basic mathematical calculations",
                input_schema={
                    "type": "object",
                    "properties": {
                        "expression": {
                            "type": "string",
                            "description": "Mathematical expression to evaluate (e.g., '2 + 3 * 4')"
                        }
                    },
                    "required": ["expression"]
                }
            ),
            MCPTool(
                name="generate_uuid",
                description="Generates a random UUID",
                input_schema={
                    "type": "object",
                    "properties": {}
                }
            )
        ]
        self.start_time = None
    
    def handle_tools_list(self, request_id: str) -> Dict[str, Any]:
        """Handle tools/list request"""
        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "result": {
                "tools": [tool.to_dict() for tool in self.tools]
            }
        }
    
    def handle_tools_call(self, request_id: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """Handle tools/call request"""
        tool_name = params.get("name")
        arguments = params.get("arguments", {})
        
        try:
            if tool_name == "echo":
                text = arguments.get("text", "")
                result = {
                    "content": [{
                        "type": "text",
                        "text": f"Echo: {text}"
                    }]
                }
            elif tool_name == "add_numbers":
                try:
                    a = arguments.get("a")
                    b = arguments.get("b")
                    
                    # Validate that both parameters are provided
                    if a is None or b is None:
                        return {
                            "jsonrpc": "2.0",
                            "id": request_id,
                            "error": {
                                "code": -32602,
                                "message": "Invalid params: Both 'a' and 'b' parameters are required"
                            }
                        }
                    else:
                        # Validate that both parameters are numbers (but not booleans)
                        if (not isinstance(a, (int, float)) or isinstance(a, bool) or 
                            not isinstance(b, (int, float)) or isinstance(b, bool)):
                            return {
                                "jsonrpc": "2.0",
                                "id": request_id,
                                "error": {
                                    "code": -32602,
                                    "message": f"Invalid params: Both parameters must be numbers. Got a={type(a).__name__}({a}), b={type(b).__name__}({b})"
                                }
                            }
                        else:
                            sum_result = a + b
                            result = {
                                "content": [{
                                    "type": "text",
                                    "text": f"The sum of {a} and {b} is {sum_result}"
                                }]
                            }
                except Exception as e:
                    return {
                        "jsonrpc": "2.0",
                        "id": request_id,
                        "error": {
                            "code": -32603,
                            "message": f"Internal error in add_numbers: {str(e)}"
                        }
                    }
            elif tool_name == "get_time":
                import datetime
                current_time = datetime.datetime.now().isoformat()
                result = {
                    "content": [{
                        "type": "text",
                        "text": f"Current server time: {current_time}"
                    }]
                }
            elif tool_name == "reverse_string":
                text = arguments.get("text", "")
                reversed_text = text[::-1]
                result = {
                    "content": [{
                        "type": "text",
                        "text": f"Reversed: {reversed_text}"
                    }]
                }
            elif tool_name == "server_info":
                import datetime
                import os
                uptime = ""
                if self.start_time:
                    uptime_seconds = (datetime.datetime.now() - self.start_time).total_seconds()
                    uptime = f"{uptime_seconds:.1f} seconds"
                
                result = {
                    "content": [{
                        "type": "text",
                        "text": f"MCP Test Server Info:\n"
                               f"- Version: 1.0.0\n"
                               f"- Protocol: 2024-11-05\n"
                               f"- Tools: {len(self.tools)}\n"
                               f"- Uptime: {uptime}\n"
                               f"- PID: {os.getpid()}\n"
                               f"- Python: {sys.version.split()[0]}"
                    }]
                }
            elif tool_name == "calculate":
                expression = arguments.get("expression", "")
                try:
                    # Safe evaluation of mathematical expressions
                    import ast
                    import operator
                    
                    # Allowed operations
                    ops = {
                        ast.Add: operator.add,
                        ast.Sub: operator.sub,
                        ast.Mult: operator.mul,
                        ast.Div: operator.truediv,
                        ast.Pow: operator.pow,
                        ast.Mod: operator.mod,
                        ast.USub: operator.neg,
                        ast.UAdd: operator.pos,
                    }
                    
                    def eval_expr(node):
                        if isinstance(node, ast.Num):  # number
                            return node.n
                        elif isinstance(node, ast.Constant):  # Python 3.8+
                            return node.value
                        elif isinstance(node, ast.BinOp):  # binary operation
                            return ops[type(node.op)](eval_expr(node.left), eval_expr(node.right))
                        elif isinstance(node, ast.UnaryOp):  # unary operation
                            return ops[type(node.op)](eval_expr(node.operand))
                        else:
                            raise TypeError(f"Unsupported operation: {type(node)}")
                    
                    tree = ast.parse(expression, mode='eval')
                    calc_result = eval_expr(tree.body)
                    
                    result = {
                        "content": [{
                            "type": "text",
                            "text": f"Expression: {expression}\nResult: {calc_result}"
                        }]
                    }
                except Exception as e:
                    return {
                        "jsonrpc": "2.0",
                        "id": request_id,
                        "error": {
                            "code": -32602,
                            "message": f"Invalid expression '{expression}': {str(e)}"
                        }
                    }
            elif tool_name == "generate_uuid":
                new_uuid = str(uuid.uuid4())
                result = {
                    "content": [{
                        "type": "text",
                        "text": f"Generated UUID: {new_uuid}"
                    }]
                }
            else:
                return {
                    "jsonrpc": "2.0",
                    "id": request_id,
                    "error": {
                        "code": -32601,
                        "message": f"Unknown tool: {tool_name}"
                    }
                }
            
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": result
            }
        except Exception as e:
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "error": {
                    "code": -32603,
                    "message": f"Internal error: {str(e)}"
                }
            }
    
    def handle_initialize(self, request_id: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """Handle initialize request"""
        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "result": {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "tools": {
                        "listChanged": True
                    }
                },
                "serverInfo": {
                    "name": "MCP Test Server",
                    "version": "1.0.0"
                }
            }
        }

class MCPRequestHandler(BaseHTTPRequestHandler):
    def __init__(self, *args, mcp_server: MCPServer = None, **kwargs):
        self.mcp_server = mcp_server or MCPServer()
        super().__init__(*args, **kwargs)
    
    def do_POST(self):
        # Handle CORS preflight
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
        
        try:
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            request_data = json.loads(post_data.decode('utf-8'))
            
            print(f"Received request: {json.dumps(request_data, indent=2)}")
            
            # Parse JSON-RPC request
            method = request_data.get("method")
            request_id = request_data.get("id")
            params = request_data.get("params", {})
            
            # Route to appropriate handler
            if method == "tools/list":
                response = self.mcp_server.handle_tools_list(request_id)
            elif method == "tools/call":
                response = self.mcp_server.handle_tools_call(request_id, params)
            elif method == "initialize":
                response = self.mcp_server.handle_initialize(request_id, params)
            else:
                response = {
                    "jsonrpc": "2.0",
                    "id": request_id,
                    "error": {
                        "code": -32601,
                        "message": f"Method not found: {method}"
                    }
                }
            
            print(f"Sending response: {json.dumps(response, indent=2)}")
            
            response_json = json.dumps(response)
            self.wfile.write(response_json.encode('utf-8'))
            
        except Exception as e:
            print(f"Error handling request: {e}")
            error_response = {
                "jsonrpc": "2.0",
                "id": None,
                "error": {
                    "code": -32700,
                    "message": f"Parse error: {str(e)}"
                }
            }
            response_json = json.dumps(error_response)
            self.wfile.write(response_json.encode('utf-8'))
    
    def do_OPTIONS(self):
        # Handle CORS preflight
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def log_message(self, format, *args):
        # Suppress default logging
        pass

def main():
    parser = argparse.ArgumentParser(description='MCP Test Server')
    parser.add_argument('--port', type=int, default=3000, help='Port to run the server on')
    parser.add_argument('--host', default='localhost', help='Host to bind the server to')
    
    args = parser.parse_args()
    
    mcp_server = MCPServer()
    # Set start time for uptime tracking
    import datetime
    mcp_server.start_time = datetime.datetime.now()
    
    def handler(*args, **kwargs):
        return MCPRequestHandler(*args, mcp_server=mcp_server, **kwargs)
    
    server = HTTPServer((args.host, args.port), handler)
    
    print(f"MCP Test Server starting on {args.host}:{args.port}")
    print(f"Available tools ({len(mcp_server.tools)}):")
    for tool in mcp_server.tools:
        print(f"  - {tool.name}: {tool.description}")
    print(f"\nServer URL: http://{args.host}:{args.port}")
    print("Press Ctrl+C to stop the server")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down server...")
        server.shutdown()

if __name__ == "__main__":
    main()
