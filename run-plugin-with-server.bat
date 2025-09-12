@echo off
REM MCP Inspector Lite - Run Plugin with Server (Windows)
REM This script starts the MCP test server and then runs the plugin

setlocal enabledelayedexpansion

REM Configuration
set SERVER_PORT=3000
set SERVER_HOST=localhost
set SERVER_URL=http://%SERVER_HOST%:%SERVER_PORT%

echo.
echo ========================================
echo MCP Inspector Lite - Development Environment
echo ========================================
echo.

REM Check if we're in the right directory
if not exist "build.gradle.kts" (
    echo Error: build.gradle.kts not found
    echo Please run this script from the mcp-plugin project root directory
    pause
    exit /b 1
)

if not exist "test-server" (
    echo Error: test-server directory not found
    echo Please run this script from the mcp-plugin project root directory
    pause
    exit /b 1
)

REM Check if Python 3 is available
python --version >nul 2>&1
if errorlevel 1 (
    echo Error: Python is not installed or not in PATH
    echo Please install Python 3.7+ and add it to PATH
    pause
    exit /b 1
)

echo Starting MCP Test Server...
echo Server will be available at: %SERVER_URL%
echo.

REM Start server in background
cd test-server
start /b python mcp_server.py --port %SERVER_PORT% --host %SERVER_HOST%
cd ..

REM Wait a moment for server to start
timeout /t 3 /nobreak >nul

echo Server started successfully!
echo.
echo Available tools:
echo   - echo: Echoes back input text
echo   - add_numbers: Adds two numbers together
echo   - get_time: Returns current server time
echo   - reverse_string: Reverses a string
echo.

REM Make gradlew executable and run plugin
echo Building and running IntelliJ plugin...
echo.

REM Build plugin
echo Building plugin...
call gradlew.bat compileKotlin
if errorlevel 1 (
    echo Failed to build plugin
    pause
    exit /b 1
)

echo Plugin built successfully!
echo.
echo Starting IntelliJ IDEA with plugin...
echo.
echo To test the plugin:
echo   1. Open 'MCP Inspector Lite' tool window
echo   2. Connect to: %SERVER_URL%
echo   3. Browse and test the available tools
echo.

REM Run the plugin
call gradlew.bat runIde

echo.
echo Plugin closed. Cleaning up...

REM Kill any remaining Python processes (MCP server)
taskkill /f /im python.exe >nul 2>&1

echo Goodbye!
pause
