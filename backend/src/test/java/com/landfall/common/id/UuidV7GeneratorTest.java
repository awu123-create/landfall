package com.landfall.common.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void generate_producesValidRfc9562Version7AndVariant2() {
        long before = System.currentTimeMillis();
        UUID id = UuidV7Generator.generate();
        long after = System.currentTimeMillis();

        assertEquals(36, id.toString().length());
        assertEquals(7, id.version());
        assertEquals(2, id.variant());
        long timestamp = extractTimestampMillis(id);
        assertTrue(timestamp >= before && timestamp <= after + 1000, "Timestamp out of expected window: " + timestamp);
    }

    @Test
    void generate_isStrictlyMonotonicWithinSameAndAcrossMilliseconds() {
        int count = 1;
        int maxIterations = 200_000;
        boolean sawSameMillisecond = false;
        boolean sawNextMillisecond = false;

        UUID previous = UuidV7Generator.generate();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);

        while ((!sawSameMillisecond || !sawNextMillisecond || count < 1000)
                && count < maxIterations
                && System.nanoTime() < deadline) {
            UUID current = UuidV7Generator.generate();
            assertTrue(
                    previous.toString().compareTo(current.toString()) < 0,
                    "Expected strictly increasing order: " + previous + " vs " + current);

            long prevTs = extractTimestampMillis(previous);
            long currTs = extractTimestampMillis(current);
            if (prevTs == currTs) {
                sawSameMillisecond = true;
            } else if (currTs > prevTs) {
                sawNextMillisecond = true;
            }
            count++;
            previous = current;
        }

        assertTrue(sawSameMillisecond, "Expected multiple UUIDs within the same millisecond");
        assertTrue(sawNextMillisecond, "Expected UUIDs spanning across millisecond boundary");
    }

    @Test
    void generate_isUniqueUnderConcurrentAccess() throws InterruptedException {
        int threads = 8;
        int perThread = 500;
        Set<UUID> generated = ConcurrentHashMap.newKeySet(threads * perThread);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            generated.add(UuidV7Generator.generate());
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        }

        assertEquals(threads * perThread, generated.size());
    }

    private static long extractTimestampMillis(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
