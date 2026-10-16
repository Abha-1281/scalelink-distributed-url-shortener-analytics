package com.scalelink.urlservice.domain.service;

/**
 * <h1>Low-Level Architecture (LLA): Distributed Twitter Snowflake 64-bit ID Generator</h1>
 *
 * <h2>1. Bitwise Layout (64 bits signed Long)</h2>
 * <pre>
 * +------------------------------------------------------------------------------------------------+
 * | 1 bit  | 41 bits: Millisecond Timestamp             | 10 bits: Worker ID   | 12 bits: Sequence |
 * | Sign=0 | (Milliseconds since Epoch: 2024-01-01)     | (0 to 1023 instances)| (0 to 4095 per ms)|
 * +------------------------------------------------------------------------------------------------+
 * </pre>
 *
 * <h2>2. Mathematical Invariants &amp; Capacity Derivations</h2>
 * <ul>
 *   <li><b>Epoch Baseline:</b> 1704067200000L (2024-01-01T00:00:00Z UTC).</li>
 *   <li><b>Maximum Lifespan:</b> (2^41 - 1) / (1000 * 60 * 60 * 24 * 365.25) &approx; 69.73 years.</li>
 *   <li><b>Maximum Cluster Scale:</b> 2^10 = 1024 unique active worker nodes simultaneously.</li>
 *   <li><b>Maximum Throughput:</b> 2^12 = 4096 IDs per millisecond per worker node (&approx; 4,096,000 IDs/sec/node).</li>
 * </ul>
 *
 * <h2>3. Bitwise Assembly Formula</h2>
 * <pre>
 * SnowflakeID = ((currentTimeMillis - EPOCH) &lt;&lt; 22) | (workerId &lt;&lt; 12) | sequence
 * </pre>
 *
 * <h2>4. Clock Backward Drift Mitigation</h2>
 * <ul>
 *   <li>If clock regression &Delta;t &le; 5ms: Suspends thread execution via wait() to allow clock catch-up.</li>
 *   <li>If clock regression &Delta;t &gt; 5ms: Throws {@link IllegalStateException} to prevent duplicate ID collision.</li>
 * </ul>
 */
public class SnowflakeIdGenerator {

    // Custom Epoch: 2024-01-01T00:00:00Z
    private static final long CUSTOM_EPOCH = 1704067200000L;

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS); // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);   // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(String.format("Worker ID must be between 0 and %d, got %d", MAX_WORKER_ID, workerId));
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long currentTimestamp = timeGen();

        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) { // Tolerable clock drift: wait it out
                try {
                    wait(offset << 1);
                    currentTimestamp = timeGen();
                    if (currentTimestamp < lastTimestamp) {
                        throw new IllegalStateException(String.format("Clock moved backwards by %d ms", lastTimestamp - currentTimestamp));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for clock sync", e);
                }
            } else {
                throw new IllegalStateException(String.format("Fatal clock drift: clock moved backwards by %d ms", offset));
            }
        }

        if (lastTimestamp == currentTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted within the same millisecond; wait for next millisecond
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    public long getWorkerId() {
        return workerId;
    }
}
