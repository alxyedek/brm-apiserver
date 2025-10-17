#!/bin/bash

# Resource-Limited Spring Boot Server Startup Script
# This script runs the BRM API server with constrained resources to test virtual threads

set -e

# Default resource limits (can be overridden via environment variables)
CPU_CORES=${CPU_CORES:-2}
HEAP_MB=${HEAP_MB:-256}
MIN_HEAP_MB=${MIN_HEAP_MB:-128}
PLATFORM_THREADS=${PLATFORM_THREADS:-2}
EXTRA_JVM_OPTS=${EXTRA_JVM_OPTS:-""}

# Calculate derived values
METASPACE_MB=$((HEAP_MB / 2))
DIRECT_MEMORY_MB=$((HEAP_MB / 4))

echo "=== BRM API Server - Resource Limited Mode ==="
echo "CPU Cores: $CPU_CORES"
echo "Heap Memory: ${MIN_HEAP_MB}MB - ${HEAP_MB}MB"
echo "Metaspace: ${METASPACE_MB}MB"
echo "Direct Memory: ${DIRECT_MEMORY_MB}MB"
echo "Platform Threads: $PLATFORM_THREADS"
echo "=============================================="

# Build the application if JAR doesn't exist
JAR_FILE="target/brm-apiserver-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "Building application..."
    ./mvnw clean package -DskipTests
fi

# Construct JVM arguments
JVM_ARGS=(
    "-XX:ActiveProcessorCount=$CPU_CORES"
    "-Xms${MIN_HEAP_MB}m"
    "-Xmx${HEAP_MB}m"
    "-XX:MaxMetaspaceSize=${METASPACE_MB}m"
    "-XX:MaxDirectMemorySize=${DIRECT_MEMORY_MB}m"
    "-XX:+UseG1GC"
    "-XX:MaxGCPauseMillis=200"
    "-XX:+UnlockExperimentalVMOptions"
    "-XX:+UseStringDeduplication"
    "-Dspring.profiles.active=limited"
    "-Dserver.tomcat.threads.max=$PLATFORM_THREADS"
    "-Dserver.tomcat.threads.min-spare=1"
    "-Dserver.tomcat.max-connections=50"
    "-Dserver.tomcat.accept-count=10"
    "-Dserver.tomcat.connection-timeout=20000"
    "-Dserver.tomcat.keep-alive-timeout=60000"
)

# Add extra JVM options if provided
if [ -n "$EXTRA_JVM_OPTS" ]; then
    JVM_ARGS+=($EXTRA_JVM_OPTS)
fi

echo "Starting server with JVM args: ${JVM_ARGS[*]}"
echo "Press Ctrl+C to stop the server"
echo ""

# Determine CPU affinity mask for taskset
if [ "$CPU_CORES" -eq 1 ]; then
    CPU_MASK="0"
elif [ "$CPU_CORES" -eq 2 ]; then
    CPU_MASK="0,1"
elif [ "$CPU_CORES" -eq 4 ]; then
    CPU_MASK="0,1,2,3"
else
    # For other values, use first N cores
    CPU_MASK="0"
    for ((i=1; i<CPU_CORES; i++)); do
        CPU_MASK="$CPU_MASK,$i"
    done
fi

# Check if taskset is available
if command -v taskset >/dev/null 2>&1; then
    echo "Using taskset to limit CPU cores to: $CPU_MASK"
    taskset -c "$CPU_MASK" java "${JVM_ARGS[@]}" -jar "$JAR_FILE"
else
    echo "Warning: taskset not available, using JVM processor count only"
    java "${JVM_ARGS[@]}" -jar "$JAR_FILE"
fi
