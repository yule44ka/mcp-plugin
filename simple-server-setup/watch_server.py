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
