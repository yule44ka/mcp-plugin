#!/bin/bash

# Script for running the MCP Plugin in IntelliJ IDEA
# This script builds and runs the plugin in a sandbox environment

set -e  # Exit on any error

echo "🚀 Starting MCP Plugin Development Environment"
echo "=============================================="

# Check if we're in the right directory
if [ ! -f "build.gradle.kts" ]; then
    echo "❌ Error: build.gradle.kts not found. Please run this script from the project root."
    exit 1
fi

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not in PATH"
    exit 1
fi

# Check if Gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo "❌ Error: Gradle wrapper not found"
    exit 1
fi

echo "📦 Building the plugin..."
./gradlew clean build

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "✅ Build successful!"
echo "🏃 Starting IntelliJ IDEA with the plugin..."
echo ""
echo "📝 Note: IntelliJ IDEA will start in sandbox mode with your plugin loaded."
echo "   You can test the MCP Inspector plugin from the Tools menu."
echo ""
echo "🛑 To stop: Close IntelliJ IDEA or press Ctrl+C in this terminal"
echo ""

# Run the plugin in IntelliJ IDEA sandbox
./gradlew runIde
