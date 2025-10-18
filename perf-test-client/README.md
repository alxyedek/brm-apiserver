# Performance Testing Client

This folder contains the load testing tools for the BRM API Server.

## Files

- `run-test-client.sh` - Generic performance test client (tests any server)
- `run-performance-test.sh` - BRM server orchestrator (manages server lifecycle)
- `load-test.py` - HTTP load testing engine
- `monitor-performance.sh` - Performance monitoring script using pidstat
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

The performance test script uses environment variables for configuration. You can set these via:

- **Shell environment variables**: `OPERATION_TYPE=SLEEP ./perf-test-client/run-performance-test.sh`
- **.env files**: Create a `.env` file or use `--env-file` option
- **Default values**: Used if not specified

### Environment Variables

#### Load Test Parameters
- **`CONCURRENT_REQUESTS`**: Number of concurrent requests (default: 50)
- **`TOTAL_REQUESTS`**: Total number of requests (default: 1000)
- **`TIMEOUT_SECONDS`**: Request timeout in seconds (default: 30)

#### Blocking Operation Parameters
- **`OPERATION_TYPE`**: Operation type: SLEEP, FILE_IO, NETWORK_IO, MIXED (default: MIXED)
- **`MIN_BLOCK_PERIOD_MS`**: Minimum block period in milliseconds (default: 500)
- **`MAX_BLOCK_PERIOD_MS`**: Maximum block period in milliseconds (default: 2000)

#### Server Resource Parameters
- **`CPU_CORES`**: Number of CPU cores for server (default: 2)
- **`HEAP_MB`**: Max heap size in MB (default: 256)
- **`MIN_HEAP_MB`**: Initial heap size in MB (default: 128)
- **`PLATFORM_THREADS`**: Max platform threads (default: 2)

### Pre-configured Environment Files

- **`environments/single-core-perf-test.env`**: Single-core test with 200 concurrent requests (your last run)
- **`environments/fast-test.env`**: Quick test that completes in ~2-3 seconds for verification

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
   OPERATION_TYPE=SLEEP ./perf-test-client/run-performance-test.sh
   ```

4. **Compare different blocking types**:
   ```bash
   # Sleep blocking
   OPERATION_TYPE=SLEEP ./perf-test-client/run-performance-test.sh
   
   # File I/O blocking
   OPERATION_TYPE=FILE_IO ./perf-test-client/run-performance-test.sh
   
   # Network I/O blocking
   OPERATION_TYPE=NETWORK_IO ./perf-test-client/run-performance-test.sh
   
   # Mixed operations
   OPERATION_TYPE=MIXED ./perf-test-client/run-performance-test.sh
   ```

5. **Test with different load parameters**:
   ```bash
   # High concurrency
   CONCURRENT_REQUESTS=200 TOTAL_REQUESTS=1000 ./perf-test-client/run-performance-test.sh
   
   # Quick test
   CONCURRENT_REQUESTS=10 TOTAL_REQUESTS=50 TIMEOUT_SECONDS=5 ./perf-test-client/run-performance-test.sh
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

### BRM Server Orchestrator (run-performance-test.sh)

Use this script to test the BRM server with full lifecycle management:

```bash
# Run with defaults or .env file
./perf-test-client/run-performance-test.sh

# Run with specific .env file
./perf-test-client/run-performance-test.sh --env-file environments/single-core-perf-test.env

# Run with environment variables directly
CPU_CORES=1 HEAP_MB=128 PLATFORM_THREADS=1 ./perf-test-client/run-performance-test.sh

# Quick test (completes in ~2-3 seconds)
./perf-test-client/run-performance-test.sh --env-file environments/fast-test.env
```

**What it does:**
- Starts/stops BRM server with resource limits
- Monitors server performance with pidstat
- Delegates testing to `run-test-client.sh`
- Reports all log files

### Generic Test Client (run-test-client.sh)

Use this script to test any server endpoint:

```bash
# Test localhost:8080 with defaults
./perf-test-client/run-test-client.sh

# Test remote server
BASE_URL=http://remote-server:8080 ./perf-test-client/run-test-client.sh

# Test with specific .env file
./perf-test-client/run-test-client.sh --env-file environments/fast-test.env

# Test with environment variables
BASE_URL=http://localhost:9090 OPERATION_TYPE=SLEEP ./perf-test-client/run-test-client.sh
```

**What it does:**
- Tests any server at any BASE_URL
- Validates test parameters
- Runs load tests
- Reports results
- Does NOT manage servers

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
