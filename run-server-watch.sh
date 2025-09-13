#!/bin/bash

# Script for running the MCP Server with auto-reload on file changes
# This script monitors Python files and restarts the server when changes are detected

set -e  # Exit on any error

echo "🔄 Starting MCP Server with Auto-Reload"
echo "======================================="

# Check if we're in the right directory
if [ ! -d "simple-server-setup" ]; then
    echo "❌ Error: simple-server-setup directory not found. Please run this script from the project root."
    exit 1
fi

# Check if Python is available
if ! command -v python &> /dev/null; then
    echo "❌ Error: Python is not installed or not in PATH"
    exit 1
fi

# Check if watchdog is installed, if not install it
echo "📦 Checking dependencies..."
python -c "import watchdog" 2>/dev/null || {
    echo "📥 Installing watchdog for file monitoring..."
    pip install watchdog
}

# Check if required MCP packages are installed
python -c "import mcp, fastmcp" 2>/dev/null || {
    echo "❌ Error: Required Python packages are not installed"
    echo "Please run: pip install mcp fastmcp python-dotenv"
    exit 1
}

echo "✅ All dependencies are available!"

# Create a Python script for watching and restarting the server
cat > simple-server-setup/watch_server.py << 'EOF'
#!/usr/bin/env python3
"""
Auto-reload MCP Server
Monitors Python files for changes and restarts the server automatically
"""

import os
import sys
import time
import signal
import subprocess
from pathlib import Path
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

class ServerRestartHandler(FileSystemEventHandler):
    def __init__(self, server_script):
        self.server_script = server_script
        self.process = None
        self.restart_server()
        
    def on_modified(self, event):
        if event.is_directory:
            return
            
        # Only restart on Python file changes
        if event.src_path.endswith('.py'):
            print(f"\n🔄 File changed: {event.src_path}")
            print("🔄 Restarting server...")
            self.restart_server()
    
    def restart_server(self):
        # Kill existing process
        if self.process:
            print("🛑 Stopping current server...")
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()
        
        # Start new process
        print(f"🚀 Starting server: {self.server_script}")
        self.process = subprocess.Popen([
            sys.executable, self.server_script
        ], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        
        # Print server output in real-time
        def print_output():
            for line in iter(self.process.stdout.readline, ''):
                if line:
                    print(f"[SERVER] {line.rstrip()}")
        
        import threading
        output_thread = threading.Thread(target=print_output, daemon=True)
        output_thread.start()
        
        print("✅ Server started successfully!")
        print("📁 Monitoring files for changes...")
        print("🛑 Press Ctrl+C to stop")

def main():
    server_script = "server.py"
    
    if not os.path.exists(server_script):
        print(f"❌ Error: {server_script} not found in current directory")
        sys.exit(1)
    
    # Setup file watcher
    event_handler = ServerRestartHandler(server_script)
    observer = Observer()
    observer.schedule(event_handler, ".", recursive=True)
    
    try:
        observer.start()
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n🛑 Shutting down...")
        observer.stop()
        if event_handler.process:
            event_handler.process.terminate()
            event_handler.process.wait()
    
    observer.join()
    print("👋 Server stopped.")

if __name__ == "__main__":
    main()
EOF

echo "🔄 Starting server with file monitoring..."
echo "📁 Monitoring directory: $(pwd)/simple-server-setup"
echo "🔍 Watching for changes in: *.py files"
echo ""
echo "💡 Tips:"
echo "   - Edit server.py or any Python file to see auto-reload in action"
echo "   - Server will restart automatically when files change"
echo "   - Press Ctrl+C to stop the server and file monitoring"
echo ""

cd simple-server-setup
python watch_server.py
