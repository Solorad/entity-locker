package com.morenkov.entitylocker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;


public class EntityLockerTest {
    private volatile long value = 0L;
    private EntityLocker<Integer, Long> entityLocker;

    private ExecutorService executorService;


    @Before
    public void setUp() {
        entityLocker = new EntityLocker<>();
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1);
        entityLocker.startScheduledCleaner(1, TimeUnit.SECONDS);
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void testLocker() {
        int incrementsRun = 5_000_000;
        executorService.submit(() -> entityLocker.invokeExclusive(2, () -> {
            System.out.println(value);
            return value;
        }));
        IntStream.range(0, incrementsRun)
                .forEach(i -> executorService.submit(() -> entityLocker.invokeExclusive(1, () -> value += 1)));
        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            System.err.println("InterruptedException occurred");
            e.printStackTrace();
        }
        assertEquals(incrementsRun, value);
    }
}