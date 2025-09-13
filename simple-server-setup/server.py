from mcp.server.fastmcp import FastMCP
from dotenv import load_dotenv
from datetime import datetime

load_dotenv("../.env")

# Create an MCP server
mcp = FastMCP(
    name="Calculator",
    host="0.0.0.0",  # only used for SSE transport (localhost)
    port=8050,  # only used for SSE transport (set this to any port)
    stateless_http=True,
)


# Add a simple calculator tool
@mcp.tool()
def add(a: int, b: int) -> int:
    """Add two numbers together"""
    return a + b


# Add a tool to get current server time
@mcp.tool()
def get_server_time() -> str:
    """Get the current date and time on the server"""
    current_time = datetime.now()
    return current_time.strftime("%Y-%m-%d %H:%M:%S")


# Add a tool for introduction
@mcp.tool()
def introduction(name: str) -> str:
    """Introduce yourself and get a nice greeting"""
    return f"{name}, you are magnificent!"


# Run the server
if __name__ == "__main__":
    transport = "sse"  # Changed to SSE for plugin testing
    if transport == "stdio":
        print("Running server with stdio transport")
        mcp.run(transport="stdio")
    elif transport == "sse":
        print("Running server with SSE transport")
        mcp.run(transport="sse")
    elif transport == "streamable-http":
        print("Running server with Streamable HTTP transport")
        mcp.run(transport="streamable-http")
    else:
        raise ValueError(f"Unknown transport: {transport}")
