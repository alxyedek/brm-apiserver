package org.brm.apiserver.misc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;

@Component
public class BlockingSimulator {

    private static final Logger log = LoggerFactory.getLogger(BlockingSimulator.class);
    private static final Random RANDOM = new Random();

    // Test data file management
    private static final String TEST_DATA_DIR = "testdata/blocking";
    private static final Map<String, Integer> TEST_FILES = Map.of(
        "file_1kb.dat", 1 * 1024,
        "file_100kb.dat", 100 * 1024,
        "file_1mb.dat", 1 * 1024 * 1024,
        "file_10mb.dat", 10 * 1024 * 1024
    );

    public enum OperationType {
        SLEEP, FILE_IO, NETWORK_IO, MIXED
    }

    @Value("${brm.blocking.operation-type:sleep}")
    private String operationTypeConfig;

    @Value("${brm.blocking.min-block-period-ms:1000}")
    private int minBlockPeriodMs;

    @Value("${brm.blocking.max-block-period-ms:5000}")
    private int maxBlockPeriodMs;

    /**
     * Initialize test data files on first use
     */
    private void ensureTestDataFiles() {
        try {
            // Create testdata/blocking directory
            Path testDataPath = Paths.get(TEST_DATA_DIR);
            if (!Files.exists(testDataPath)) {
                Files.createDirectories(testDataPath);
                log.info("Created test data directory: {}", TEST_DATA_DIR);
            }

            // Create test files if they don't exist
            for (Map.Entry<String, Integer> entry : TEST_FILES.entrySet()) {
                String filename = entry.getKey();
                int size = entry.getValue();
                File testFile = new File(TEST_DATA_DIR, filename);

                if (!testFile.exists()) {
                    createTestFile(testFile, size);
                    log.info("Created test data file: {} ({} bytes)", filename, size);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to initialize test data files", e);
        }
    }

    /**
     * Create a test file with random data
     */
    private void createTestFile(File file, int size) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int remaining = size;
            
            while (remaining > 0) {
                int toWrite = Math.min(buffer.length, remaining);
                RANDOM.nextBytes(buffer);
                fos.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
            fos.flush();
        }
    }

    /**
     * Select appropriate test file based on duration
     */
    private File selectTestFile(int durationMs) {
        String filename;
        if (durationMs < 100) {
            filename = "file_1kb.dat";
        } else if (durationMs < 500) {
            filename = "file_100kb.dat";
        } else if (durationMs < 2000) {
            filename = "file_1mb.dat";
        } else {
            filename = "file_10mb.dat";
        }
        return new File(TEST_DATA_DIR, filename);
    }

    /**
     * Read a test file completely with small buffer to force I/O syscalls
     */
    private int readTestFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096]; // Small buffer for syscalls
            int totalRead = 0;
            int n;
            while ((n = fis.read(buffer)) != -1) {
                totalRead += n;
            }
            return totalRead;
        }
    }

    public void performBlockingOperation() {
        performBlockingOperation(null, null, null);
    }

    public void performBlockingOperation(String operationType, Integer minBlockPeriodMs, Integer maxBlockPeriodMs) {
        OperationType resolvedOperationType = determineOperationType(operationType);
        int resolvedMinMs = minBlockPeriodMs != null ? minBlockPeriodMs : this.minBlockPeriodMs;
        int resolvedMaxMs = maxBlockPeriodMs != null ? maxBlockPeriodMs : this.maxBlockPeriodMs;
        int durationMs = generateRandomDuration(resolvedMinMs, resolvedMaxMs);
        
        switch (resolvedOperationType) {
            case SLEEP:
                log.info("Performing blocking operation: {} for {}ms (min: {}, max: {})", 
                        resolvedOperationType, durationMs, resolvedMinMs, resolvedMaxMs);
                performSleepBlocking(durationMs);
                break;
            case FILE_IO:
                log.info("Performing blocking operation: {} for {}ms (min: {}, max: {})", 
                        resolvedOperationType, durationMs, resolvedMinMs, resolvedMaxMs);
                performFileIoBlocking(durationMs);
                break;
            case NETWORK_IO:
                log.info("Performing blocking operation: {} for {}ms (min: {}, max: {})", 
                        resolvedOperationType, durationMs, resolvedMinMs, resolvedMaxMs);
                performNetworkIoBlocking(durationMs);
                break;
            case MIXED:
                performMixedBlocking(durationMs, resolvedMinMs, resolvedMaxMs);
                break;
            default:
                log.warn("Unknown operation type: {}, defaulting to sleep", resolvedOperationType);
                performSleepBlocking(durationMs);
                break;
        }
    }


    private OperationType determineOperationType(String operationType) {
        String typeToUse = operationType != null ? operationType : operationTypeConfig;
        try {
            return OperationType.valueOf(typeToUse.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid operation type '{}', defaulting to SLEEP", typeToUse);
            return OperationType.SLEEP;
        }
    }


    private int generateRandomDuration(int minMs, int maxMs) {
        if (minMs >= maxMs) {
            return minMs;
        }
        return RANDOM.nextInt(minMs, maxMs + 1);
    }

    private void performSleepBlocking(int durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep blocking was interrupted", e);
        }
    }

    private void performFileIoBlocking(int durationMs) {
        // Initialize test data files on first use
        ensureTestDataFiles();
        
        try {
            // Select appropriate test file based on duration
            File testFile = selectTestFile(durationMs);
            
            // Calculate read iterations based on file size and duration
            int readIterations;
            if (durationMs < 100) {
                readIterations = durationMs / 2; // ~2ms per read (1KB file)
            } else if (durationMs < 500) {
                readIterations = durationMs / 5; // ~5ms per read (100KB file)
            } else if (durationMs < 2000) {
                readIterations = durationMs / 10; // ~10ms per read (1MB file)
            } else {
                readIterations = durationMs / 20; // ~20ms per read (10MB file)
            }
            
            if (readIterations < 1) {
                readIterations = 1;
            }
            
            // Read the file multiple times to achieve desired duration
            long startTime = System.currentTimeMillis();
            int totalBytesRead = 0;
            
            for (int i = 0; i < readIterations; i++) {
                int bytesRead = readTestFile(testFile);
                totalBytesRead += bytesRead;
                
                // Add small delay between reads to simulate I/O processing time
                // This ensures we achieve the desired blocking duration even with cached files
                long elapsed = System.currentTimeMillis() - startTime;
                long remainingTime = durationMs - elapsed;
                
                if (remainingTime > 0 && i < readIterations - 1) { // Don't delay after last iteration
                    // Distribute remaining time across remaining iterations
                    long delayPerIteration = remainingTime / (readIterations - i - 1);
                    if (delayPerIteration > 0) {
                        Thread.sleep(delayPerIteration);
                    }
                }
            }
            
            log.debug("File I/O blocking completed: {}ms, {} iterations, {} bytes read", 
                    durationMs, readIterations, totalBytesRead);
            
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("File I/O blocking encountered an issue, falling back to sleep", e);
            performSleepBlocking(durationMs);
        }
    }

    private void performNetworkIoBlocking(int durationMs) {
        // Use non-routable TEST-NET IPs (RFC 5737) to create realistic network blocking
        // Split duration into thirds for multiple connection attempts
        
        int timeoutPerAttempt = durationMs / 3;
        if (timeoutPerAttempt < 1) {
            timeoutPerAttempt = 1;
        }
        
        // Attempt 1: TCP connect to 192.0.2.1:80 (TEST-NET-1)
        try (Socket socket1 = new Socket()) {
            socket1.connect(new InetSocketAddress("192.0.2.1", 80), timeoutPerAttempt);
            log.debug("Network I/O attempt 1 completed unexpectedly");
        } catch (IOException e) {
            log.debug("Network I/O attempt 1 completed: {} (expected)", e.getMessage());
        }
        
        // Attempt 2: TCP connect to 198.51.100.1:80 (TEST-NET-2)
        try (Socket socket2 = new Socket()) {
            socket2.connect(new InetSocketAddress("198.51.100.1", 80), timeoutPerAttempt);
            log.debug("Network I/O attempt 2 completed unexpectedly");
        } catch (IOException e) {
            log.debug("Network I/O attempt 2 completed: {} (expected)", e.getMessage());
        }
        
        // Attempt 3: TCP connect to 203.0.113.1:53 (TEST-NET-3)
        try (Socket socket3 = new Socket()) {
            socket3.connect(new InetSocketAddress("203.0.113.1", 53), timeoutPerAttempt);
            log.debug("Network I/O attempt 3 completed unexpectedly");
        } catch (IOException e) {
            log.debug("Network I/O attempt 3 completed: {} (expected)", e.getMessage());
        }
        
        log.debug("Network I/O blocking completed: {}ms total duration", durationMs);
    }

    private void performMixedBlocking(int durationMs, int minMs, int maxMs) {
        // Randomly select one of the three operation types
        OperationType[] types = {OperationType.SLEEP, OperationType.FILE_IO, OperationType.NETWORK_IO};
        OperationType selectedType = types[RANDOM.nextInt(types.length)];
        
        log.info("Performing blocking operation: MIXED (selected: {}) for {}ms (min: {}, max: {})", 
                selectedType, durationMs, minMs, maxMs);
        
        switch (selectedType) {
            case SLEEP:
                performSleepBlocking(durationMs);
                break;
            case FILE_IO:
                performFileIoBlocking(durationMs);
                break;
            case NETWORK_IO:
                performNetworkIoBlocking(durationMs);
                break;
            case MIXED:
                // This shouldn't happen in mixed mode, but handle it gracefully
                log.warn("MIXED operation selected within mixed mode, defaulting to sleep");
                performSleepBlocking(durationMs);
                break;
            default:
                log.warn("Unknown operation type in mixed mode: {}, defaulting to sleep", selectedType);
                performSleepBlocking(durationMs);
                break;
        }
    }
}
