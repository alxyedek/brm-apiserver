#!/bin/bash

# Stop BRM API Server Script
# This script stops the BRM API server running with limited resources

echo "🛑 Stopping BRM API Server..."

# Find and kill the Java process running brm-apiserver
SERVER_PIDS=$(pgrep -f "java.*brm-apiserver.*jar" || true)

if [ -z "$SERVER_PIDS" ]; then
    echo "No BRM API server process found."
    exit 0
fi

echo "Found server processes: $SERVER_PIDS"

# Kill the processes gracefully first
for pid in $SERVER_PIDS; do
    echo "Stopping server process (PID: $pid)..."
    kill "$pid" 2>/dev/null || true
done

# Wait a moment for graceful shutdown
sleep 3

# Force kill if still running
REMAINING_PIDS=$(pgrep -f "java.*brm-apiserver.*jar" || true)
if [ -n "$REMAINING_PIDS" ]; then
    echo "Force stopping remaining processes..."
    for pid in $REMAINING_PIDS; do
        echo "Force killing server process (PID: $pid)..."
        kill -9 "$pid" 2>/dev/null || true
    done
fi

# Verify all processes are stopped
FINAL_CHECK=$(pgrep -f "java.*brm-apiserver.*jar" || true)
if [ -z "$FINAL_CHECK" ]; then
    echo "✅ BRM API server stopped successfully."
else
    echo "⚠️  Some processes may still be running: $FINAL_CHECK"
    exit 1
fi
