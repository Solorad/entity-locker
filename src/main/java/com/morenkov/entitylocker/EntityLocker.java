package com.morenkov.entitylocker;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * EntityLocker class implementation.
 */
public class EntityLocker<T, V> {

    private final ConcurrentMap<T, ReentrantLock> lockEntityMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduledExecutorService;
    private AtomicBoolean cleanerStarted = new AtomicBoolean();
    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();


    /**
     * Executes protected code with synchronization on entity.
     *
     * @param id     ID of entity which needs to be locked
     * @param action code that needs to be executed
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     */
    public V invokeExclusive(T id, Supplier<V> action) throws InterruptedException {
        readWriteLock.readLock().lock();
        ReentrantLock entityLock = getLock(id);
        try {
            entityLock.lock();
            return action.get();
        } finally {
            entityLock.unlock();
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Executes protected code with synchronization on entity.
     *
     * @param id       ID of entity which needs to be locked
     * @param action   code that needs to be executed
     * @param timeOut  timeout after which thread is interrupted
     * @param timeUnit time unit of the {@code time} parameter
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     */
    public V invokeExclusive(T id, Supplier<V> action, long timeOut, TimeUnit timeUnit) throws InterruptedException {
        ReentrantLock entityLock = getLock(id);
        readWriteLock.readLock().lock();
        try {
            if (!entityLock.tryLock(timeOut, timeUnit)) {
                throw new InterruptedException("Problem appeared with lock acquiring!");
            }
            return action.get();
        } finally {
            readWriteLock.readLock().unlock();
            entityLock.unlock();
        }
    }

    private ReentrantLock getLock(T id) {
        if (id == null) {
            throw new IllegalArgumentException("Entity ID cannot be null");
        }
        return lockEntityMap.computeIfAbsent(id, lock -> new ReentrantLock());
    }

    /**
     * Not configurable after first call.
     *
     * @param delay
     * @param unit
     */
    public void startScheduledCleaner(long delay, TimeUnit unit) {
        if (cleanerStarted.get()) {
            return;
        }
        if (cleanerStarted.compareAndSet(false, true)) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());
            scheduledExecutorService.scheduleWithFixedDelay(() -> {
                readWriteLock.writeLock().lock();
                lockEntityMap.forEach((id, lock) -> {
                boolean isAquired = lock.tryLock();
                try {
                    if (isAquired && lock.getQueueLength() == 0) {
                        lockEntityMap.remove(id);
                        System.out.println("Id '" + id + "' was removed.");

                    }
                } finally {
                    readWriteLock.writeLock().unlock();
                    lock.unlock();
                }
            });}, 1, delay, unit);

        }
    }

    static class DaemonThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("Scheduled Cleaner Thread");
            return thread;
        }
    }

    protected void finalize() throws IOException {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }
    }

}
