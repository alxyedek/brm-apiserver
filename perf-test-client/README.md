# Performance Testing Client

This folder contains the load testing tools for the BRM API Server.

## Files

- `load-test.py` - Main load testing script
- `monitor-performance.sh` - Performance monitoring script using pidstat
- `run-performance-test.sh` - Automated performance test runner
- `requirements.txt` - Python dependencies (none required - uses standard library)
- `README.md` - This documentation

## Related Scripts (in build/ directory)

- `start-limited.sh` - Start server with limited resources
- `stop-limited.sh` - Stop the BRM API server

## Quick Start

All commands should be run from the project root directory:

```bash
# Basic load test
python3 perf-test-client/load-test.py --url http://localhost:8080/rest/simple --concurrent 10 --total 100

# Test blocking endpoint with custom parameters
python3 perf-test-client/load-test.py --url "http://localhost:8080/rest/blocking?operation-type=SLEEP&min-block-period-ms=100&max-block-period-ms=500" --concurrent 20 --total 500

# High concurrency test
python3 perf-test-client/load-test.py --url http://localhost:8080/rest/blocking --concurrent 50 --total 1000 --timeout 5
```

## Features

- **Concurrent HTTP requests** using ThreadPoolExecutor
- **Warmup phase** to validate connection and warm up the server
- **Configurable parameters**: URL, concurrency, total requests, timeout
- **Real-time progress tracking** with progress bar
- **Comprehensive statistics**: success rate, response times, percentiles
- **Graceful interruption** (Ctrl+C) shows partial results
- **No external dependencies** - uses only Python standard library

## Command Line Options

- `--url` (required): Target URL to test
- `--concurrent` (default: 10): Number of concurrent requests
- `--total` (default: 100): Total number of requests to make
- `--timeout` (default: 30): Request timeout in seconds

## Valid Operation Types

When testing the `/rest/blocking` endpoint, you can specify the `operation-type` parameter:

- `SLEEP` - Simple thread sleep blocking
- `FILE_IO` - File I/O operations (create, write, read, delete temp files)
- `NETWORK_IO` - Network I/O operations (socket connection attempts)
- `MIXED` - Randomly selects one of the above three types for each request

**Note**: The operation type names are case-insensitive and will be converted to uppercase internally. We recommend using uppercase for consistency.

## Configurable Parameters

The performance test script supports extensive configuration via command-line options and environment variables:

### Load Test Parameters
- **`-c, --concurrent NUM`**: Number of concurrent requests (default: 50)
- **`-t, --total NUM`**: Total number of requests (default: 1000)
- **`-T, --timeout SECONDS`**: Request timeout in seconds (default: 30)

### Blocking Operation Parameters
- **`-o, --operation-type TYPE`**: Operation type (default: MIXED)
- **`-m, --min-block-ms MS`**: Minimum block period in milliseconds (default: 500)
- **`-M, --max-block-ms MS`**: Maximum block period in milliseconds (default: 2000)

### Server Resource Parameters
- **`CPU_CORES`**: Number of CPU cores for server (default: 2)
- **`HEAP_MB`**: Max heap size in MB (default: 256)
- **`MIN_HEAP_MB`**: Initial heap size in MB (default: 128)
- **`PLATFORM_THREADS`**: Max platform threads (default: 2)

## Example Output

```
=== Load Test Configuration ===
URL: http://localhost:8080/rest/blocking
Concurrent requests: 20
Total requests: 100
Timeout: 10s
================================

Warmup... (20 requests)
Warmup complete. Starting test in 1 second...

Running load test: [====================] 100/100 (100.0%)

==================================================
=== Load Test Results ===
Total requests: 100
Successful (200 OK): 100 (100.0%)
Failed/Timeout: 0 (0.0%)
Total elapsed time: 2.56s

Response Time Statistics (ms):
  Average: 371.7
  Median (50th): 373.1
  Min: 214.8
  Max: 526.8
  95th percentile: 512.1
  99th percentile: 526.8
==================================================
```

## Testing Virtual Threads

This tool is designed to test the performance of virtual threads under various load conditions:

1. **Start the server with limited resources**:
   ```bash
   ./build/start-limited.sh > logs/server.log 2>&1 &
   ```

2. **Test simple endpoint** (no blocking):
   ```bash
   python3 perf-test-client/load-test.py --url http://localhost:8080/rest/simple --concurrent 50 --total 1000
   ```

3. **Test blocking endpoint** (simulates I/O operations):
   ```bash
   python3 perf-test-client/load-test.py --url "http://localhost:8080/rest/blocking?operation-type=SLEEP" --concurrent 100 --total 2000
   ```

4. **Compare different blocking types**:
   ```bash
   # Sleep blocking
   python3 perf-test-client/load-test.py --url "http://localhost:8080/rest/blocking?operation-type=SLEEP" --concurrent 20 --total 500
   
   # File I/O blocking
   python3 perf-test-client/load-test.py --url "http://localhost:8080/rest/blocking?operation-type=FILE_IO" --concurrent 20 --total 500
   
   # Network I/O blocking
   python3 perf-test-client/load-test.py --url "http://localhost:8080/rest/blocking?operation-type=NETWORK_IO" --concurrent 20 --total 500
   
   python3 perf-test-client/load-test.py --url "http://localhost:8080/rest/blocking?operation-type=NETWORK_IO&min-block-period-ms=500&max-block-period-ms=2000" --concurrent 60 --total 180

   # Mixed operations
   python3 perf-test-client/load-test.py --url "http://localhost:8080/rest/blocking?operation-type=MIXED" --concurrent 20 --total 500
   ```

## Performance Monitoring

Monitor server performance during load testing using `pidstat`:

### Quick Monitoring (One-liner)
```bash
# Monitor for 60 seconds with 1-second intervals
pidstat -p $(pgrep -f "java.*brm-apiserver.*jar" | head -1) 1 60 > logs/pidstat-$(date +%Y%m%d-%H%M%S).log
```

### Advanced Monitoring (Script)
Use the provided monitoring script for more control:

```bash
# Basic monitoring (60 seconds, 1s intervals)
./perf-test-client/monitor-performance.sh

# Custom duration and interval
./perf-test-client/monitor-performance.sh -i 2 -d 120

# Custom output file
./perf-test-client/monitor-performance.sh -o logs/my-test.log

# Using environment variables
INTERVAL=5 DURATION=300 ./perf-test-client/monitor-performance.sh
```

### Automated Performance Test Runner

Use the automated script for a complete test with monitoring. The script will automatically start the server if it's not running and stop it when the test completes:

```bash
# Run with default parameters (MIXED operations, 500-2000ms blocking)
./perf-test-client/run-performance-test.sh

# Run with custom operation type
./perf-test-client/run-performance-test.sh -o FILE_IO

# Run with custom blocking periods
./perf-test-client/run-performance-test.sh -o SLEEP -m 100 -M 500

# Run with custom load test parameters
./perf-test-client/run-performance-test.sh -c 100 -t 2000 -T 10

# Run with custom operation and load parameters
./perf-test-client/run-performance-test.sh -o NETWORK_IO -c 20 -t 500 -T 5

# Using environment variables
OPERATION_TYPE=NETWORK_IO ./perf-test-client/run-performance-test.sh

# Test with limited resources (1 CPU core, 128MB heap, 1 platform thread)
CPU_CORES=1 HEAP_MB=128 PLATFORM_THREADS=1 ./perf-test-client/run-performance-test.sh

# Test with minimal resources (1 CPU core, 64MB heap, 1 platform thread)
CPU_CORES=1 HEAP_MB=64 MIN_HEAP_MB=32 PLATFORM_THREADS=1 ./perf-test-client/run-performance-test.sh
```

### Server Management Scripts

```bash
# Start server with limited resources
./build/start-limited.sh > logs/server.log 2>&1 &

# Stop server
./build/stop-limited.sh
```

### Manual Performance Test Workflow
```bash
# 1. Start server with logging
./build/start-limited.sh > logs/server.log 2>&1 &

# 2. Start performance monitoring in background
./perf-test-client/monitor-performance.sh -d 300 > logs/monitor.log 2>&1 &

# 3. Run load test
python3 perf-test-client/load-test.py --url "http://localhost:8080/rest/blocking?operation-type=NETWORK_IO&min-block-period-ms=500&max-block-period-ms=2000" --concurrent 50 --total 2500

# 4. Stop monitoring (if still running)
pkill -f "pidstat.*brm-apiserver"

# 5. View results
cat logs/pidstat-*.log
```

### Performance Metrics
The `pidstat` output includes:
- **CPU usage** (%usr, %system, %guest, %wait, %CPU)
- **Memory usage** (RSS, VSZ)
- **I/O statistics** (kB_rd/s, kB_wr/s, kB_ccwr/s)
- **Context switches** (cswch/s, nvcswch/s)

### Resource Limiting and Virtual Threads

The performance test script uses OS-level resource limiting for accurate testing:

- **CPU Core Limiting**: Uses `taskset` to restrict the process to specific CPU cores
- **Memory Limiting**: JVM heap and metaspace limits via `-Xmx`, `-XX:MaxMetaspaceSize`
- **Platform Thread Limiting**: Tomcat thread pool configuration
- **Virtual Thread Efficiency**: Demonstrates how virtual threads handle high concurrency with limited platform threads

**Resource Limiting Examples:**
```bash
# Minimal resources (1 CPU core, 64MB heap, 1 platform thread)
CPU_CORES=1 HEAP_MB=64 PLATFORM_THREADS=1 ./perf-test-client/run-performance-test.sh

# Standard limited resources (2 CPU cores, 256MB heap, 2 platform threads)
CPU_CORES=2 HEAP_MB=256 PLATFORM_THREADS=2 ./perf-test-client/run-performance-test.sh
```

**Expected Behavior:**
- **CPU Usage**: Should be reasonable (not >100%) when properly limited
- **High Concurrency**: Virtual threads allow thousands of concurrent requests with minimal platform threads
- **Memory Efficiency**: Virtual threads use much less memory than platform threads

## Interruption

Press `Ctrl+C` during a test to interrupt it gracefully. The tool will:
- Stop accepting new requests
- Wait for ongoing requests to complete
- Show statistics for completed requests only
- Display a warning that results are partial
