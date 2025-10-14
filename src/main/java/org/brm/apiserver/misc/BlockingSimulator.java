package org.brm.apiserver.misc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Random;

@Component
public class BlockingSimulator {

    private static final Logger log = LoggerFactory.getLogger(BlockingSimulator.class);
    private static final Random RANDOM = new Random();

    public enum OperationType {
        SLEEP, FILE_IO, NETWORK_IO, MIXED
    }

    @Value("${brm.blocking.operation-type:sleep}")
    private String operationTypeConfig;

    @Value("${brm.blocking.min-block-period-ms:1000}")
    private int minBlockPeriodMs;

    @Value("${brm.blocking.max-block-period-ms:5000}")
    private int maxBlockPeriodMs;

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
        File tempFile = null;
        try {
            // Create temporary file
            tempFile = File.createTempFile("brm_blocking_test", ".tmp");
            
            // Write some data to simulate I/O
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] data = new byte[1024];
                RANDOM.nextBytes(data);
                fos.write(data);
                fos.flush();
            }
            
            // Sleep for a portion of the duration
            Thread.sleep(durationMs / 2);
            
            // Read the data back to simulate more I/O
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                while (fis.read(buffer) != -1) {
                    // Simulate processing
                    Thread.sleep(10);
                }
            }
            
            // Sleep for remaining duration
            Thread.sleep(durationMs / 2);
            
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("File I/O blocking encountered an issue", e);
        } finally {
            // Clean up temporary file
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    private void performNetworkIoBlocking(int durationMs) {
        try (Socket socket = new Socket()) {
            // Attempt to connect to localhost on a port that's likely not listening
            // This will cause a connection timeout, simulating network I/O blocking
            socket.setSoTimeout(durationMs);
            
            // Try to connect to a port that's likely not listening (simulates network delay)
            socket.connect(new java.net.InetSocketAddress("localhost", 12345), durationMs);
            
        } catch (SocketTimeoutException e) {
            // Expected - this simulates network timeout/blocking
            log.debug("Network I/O blocking completed with timeout (expected)");
        } catch (IOException e) {
            // Expected - connection refused or other network issues
            log.debug("Network I/O blocking completed with connection error (expected)");
        }
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
