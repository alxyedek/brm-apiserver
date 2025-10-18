#!/bin/bash

# Performance Test Runner Script for BRM API Server
# This script runs a single performance test with monitoring

set -e

# Default values
OPERATION_TYPE=${OPERATION_TYPE:-"MIXED"}
MIN_BLOCK_PERIOD_MS=${MIN_BLOCK_PERIOD_MS:-"500"}
MAX_BLOCK_PERIOD_MS=${MAX_BLOCK_PERIOD_MS:-"2000"}

# Load test parameters (can be overridden via environment variables)
CONCURRENT_REQUESTS=${CONCURRENT_REQUESTS:-50}
TOTAL_REQUESTS=${TOTAL_REQUESTS:-1000}
TIMEOUT_SECONDS=${TIMEOUT_SECONDS:-30}

# Server resource limits (can be overridden via environment variables)
CPU_CORES=${CPU_CORES:-2}
HEAP_MB=${HEAP_MB:-256}
MIN_HEAP_MB=${MIN_HEAP_MB:-128}
PLATFORM_THREADS=${PLATFORM_THREADS:-2}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Run a single performance test for BRM API Server"
    echo ""
    echo "Options:"
    echo "  -h, --help                   Show this help message"
    echo "  -e, --env-file FILE          Load environment from file (default: .env)"
    echo ""
    echo "Environment variables (can be set via shell or .env file):"
    echo "  OPERATION_TYPE               Operation type: SLEEP, FILE_IO, NETWORK_IO, MIXED (default: MIXED)"
    echo "  MIN_BLOCK_PERIOD_MS          Minimum block period in milliseconds (default: 500)"
    echo "  MAX_BLOCK_PERIOD_MS          Maximum block period in milliseconds (default: 2000)"
    echo "  CONCURRENT_REQUESTS          Number of concurrent requests (default: 50)"
    echo "  TOTAL_REQUESTS               Total number of requests (default: 1000)"
    echo "  TIMEOUT_SECONDS              Request timeout in seconds (default: 30)"
    echo "  CPU_CORES                    Number of CPU cores for server (default: 2)"
    echo "  HEAP_MB                      Max heap size in MB (default: 256)"
    echo "  MIN_HEAP_MB                  Initial heap size in MB (default: 128)"
    echo "  PLATFORM_THREADS             Max platform threads (default: 2)"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Use defaults or .env file"
    echo "  $0 --env-file single-core-perf-test.env"
    echo "  OPERATION_TYPE=SLEEP CONCURRENT_REQUESTS=200 $0"
}

# Function to cleanup processes
cleanup() {
    echo ""
    echo "🧹 Cleaning up..."
    
    # Stop monitoring script if still running
    local monitor_pid=$(pgrep -f "monitor-performance.sh" | head -1)
    if [ -n "$monitor_pid" ]; then
        echo "Stopping performance monitoring (PID: $monitor_pid)..."
        kill "$monitor_pid" 2>/dev/null || true
        sleep 1
        # Force kill if still running
        kill -9 "$monitor_pid" 2>/dev/null || true
    fi
    
    # Stop pidstat processes if still running
    local pidstat_pids=$(pgrep -f "pidstat.*brm-apiserver" || true)
    if [ -n "$pidstat_pids" ]; then
        echo "Stopping pidstat processes..."
        echo "$pidstat_pids" | xargs kill 2>/dev/null || true
        sleep 1
        echo "$pidstat_pids" | xargs kill -9 2>/dev/null || true
    fi
    
    # Stop the server if we started it
    if [ "$SERVER_STARTED_BY_SCRIPT" = "true" ]; then
        echo "Stopping BRM API server..."
        ./build/stop-limited.sh
    fi
    
    echo "Cleanup completed."
}

# Set up signal handlers for cleanup
trap cleanup EXIT INT TERM

# Default .env file location
ENV_FILE=".env"

# Parse minimal command line arguments (only --help and --env-file)
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -e|--env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Load .env file if it exists
if [ -f "$ENV_FILE" ]; then
    echo "Loading environment from: $ENV_FILE"
    # Export variables from .env file (ignoring comments and empty lines)
    export $(grep -v '^#' "$ENV_FILE" | grep -v '^[[:space:]]*$' | xargs)
fi

# Validate arguments
if ! [[ "$MIN_BLOCK_PERIOD_MS" =~ ^[0-9]+$ ]] || [ "$MIN_BLOCK_PERIOD_MS" -lt 0 ]; then
    echo "Error: min-block-ms must be a non-negative integer"
    exit 1
fi

if ! [[ "$MAX_BLOCK_PERIOD_MS" =~ ^[0-9]+$ ]] || [ "$MAX_BLOCK_PERIOD_MS" -lt 0 ]; then
    echo "Error: max-block-ms must be a non-negative integer"
    exit 1
fi

if [ "$MIN_BLOCK_PERIOD_MS" -gt "$MAX_BLOCK_PERIOD_MS" ]; then
    echo "Error: min-block-ms cannot be greater than max-block-ms"
    exit 1
fi

# Validate load test parameters
if ! [[ "$CONCURRENT_REQUESTS" =~ ^[0-9]+$ ]] || [ "$CONCURRENT_REQUESTS" -lt 1 ]; then
    echo "Error: concurrent must be a positive integer"
    exit 1
fi

if ! [[ "$TOTAL_REQUESTS" =~ ^[0-9]+$ ]] || [ "$TOTAL_REQUESTS" -lt 1 ]; then
    echo "Error: total must be a positive integer"
    exit 1
fi

if ! [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || [ "$TIMEOUT_SECONDS" -lt 1 ]; then
    echo "Error: timeout must be a positive integer"
    exit 1
fi

if [ "$CONCURRENT_REQUESTS" -gt "$TOTAL_REQUESTS" ]; then
    echo "Error: concurrent cannot be greater than total"
    exit 1
fi

# Create logs directory if it doesn't exist
mkdir -p logs

# Generate timestamp for this test run
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

echo "🚀 Running Performance Test"
echo "=========================="
echo "Operation Type: $OPERATION_TYPE"
echo "Block Period: ${MIN_BLOCK_PERIOD_MS}ms - ${MAX_BLOCK_PERIOD_MS}ms"
echo "Load Test: ${CONCURRENT_REQUESTS} concurrent, ${TOTAL_REQUESTS} total requests, ${TIMEOUT_SECONDS}s timeout"
echo "Server Resources: ${CPU_CORES} cores, ${HEAP_MB}MB heap, ${PLATFORM_THREADS} platform threads"
echo "Timestamp: $TIMESTAMP"
echo ""

# Check if server is running, start if not
SERVER_STARTED_BY_SCRIPT="false"
if ! pgrep -f "java.*brm-apiserver.*jar" > /dev/null; then
    echo "📋 Starting BRM API server..."
    CPU_CORES="$CPU_CORES" HEAP_MB="$HEAP_MB" MIN_HEAP_MB="$MIN_HEAP_MB" PLATFORM_THREADS="$PLATFORM_THREADS" \
        ./build/start-limited.sh > "logs/server-$TIMESTAMP.log" 2>&1 &
    SERVER_PID=$!
    SERVER_STARTED_BY_SCRIPT="true"
    echo "Server started with PID: $SERVER_PID"
    
    # Wait for server to start
    echo "Waiting for server to be ready..."
    for i in {1..30}; do
        if curl -s http://localhost:8080/rest/simple > /dev/null 2>&1; then
            echo "✅ Server is ready!"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "❌ Server failed to start within 30 seconds"
            exit 1
        fi
        sleep 1
    done
else
    echo "✅ Server is already running"
fi

# Start performance monitoring in background
echo "📊 Starting performance monitoring..."
./perf-test-client/monitor-performance.sh -d 100 -o "logs/pidstat-$TIMESTAMP.log" > "logs/monitor-$TIMESTAMP.log" 2>&1 &
MONITOR_PID=$!

# Run the load test
echo "🔥 Running load test..."
LOAD_TEST_URL="http://localhost:8080/rest/blocking?operation-type=${OPERATION_TYPE}&min-block-period-ms=${MIN_BLOCK_PERIOD_MS}&max-block-period-ms=${MAX_BLOCK_PERIOD_MS}"

python3 perf-test-client/load-test.py \
    --url "$LOAD_TEST_URL" \
    --concurrent "$CONCURRENT_REQUESTS" \
    --total "$TOTAL_REQUESTS" \
    --timeout "$TIMEOUT_SECONDS"

LOAD_TEST_EXIT_CODE=$?

echo ""
echo "⏳ Stopping monitoring..."
# Stop monitoring if still running
if kill -0 "$MONITOR_PID" 2>/dev/null; then
    kill "$MONITOR_PID" 2>/dev/null || true
    sleep 1
    kill -9 "$MONITOR_PID" 2>/dev/null || true
fi

# Stop any remaining pidstat processes
pkill -f "pidstat.*brm-apiserver" 2>/dev/null || true

echo ""
if [ $LOAD_TEST_EXIT_CODE -eq 0 ]; then
    echo "✅ Performance test completed successfully!"
else
    echo "❌ Performance test completed with errors (exit code: $LOAD_TEST_EXIT_CODE)"
fi

echo ""
echo "📁 Log files:"
if [ "$SERVER_STARTED_BY_SCRIPT" = "true" ]; then
    echo "  Server:      logs/server-$TIMESTAMP.log"
fi
echo "  Performance: logs/pidstat-$TIMESTAMP.log"
echo "  Monitor:     logs/monitor-$TIMESTAMP.log"
