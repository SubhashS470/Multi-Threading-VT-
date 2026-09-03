package com.tmg.common;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe queue for passing RecordBatches between worker threads.
 * 
 * Purpose: Coordinate batch transfer between parsing, mapping, and writing threads.
 * Uses: Java's BlockingQueue for automatic thread synchronization.
 * 
 * Behavior:
 * - put() blocks if queue is full (producer waits)
 * - take() blocks if queue is empty (consumer waits)
 * - Capacity limited to 20 batches (prevents memory explosion)
 */
public class RecordQueue {
    
    private static final int DEFAULT_CAPACITY = 20;
    private final BlockingQueue<RecordBatch> queue;
    
    /**
     * Create queue with default capacity (20 batches).
     */
    public RecordQueue() {
        this.queue = new LinkedBlockingQueue<>(DEFAULT_CAPACITY);
    }
    
    /**
     * Create queue with custom capacity.
     * 
     * @param capacity Max number of batches to hold
     */
    public RecordQueue(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }
    
    /**
     * Add batch to queue. Blocks if queue is full.
     * 
     * @param batch RecordBatch to add
     * @throws InterruptedException If thread is interrupted while waiting
     */
    public void put(RecordBatch batch) throws InterruptedException {
        queue.put(batch);
    }
    
    /**
     * Add batch with timeout. Returns false if timeout occurs.
     * 
     * @param batch RecordBatch to add
     * @param timeout How long to wait
     * @param unit TimeUnit (SECONDS, MILLISECONDS, etc)
     * @return true if added successfully, false if timeout
     * @throws InterruptedException If thread interrupted
     */
    public boolean putWithTimeout(
            RecordBatch batch,
            long timeout,
            TimeUnit unit) throws InterruptedException {
        return queue.offer(batch, timeout, unit);
    }
    
    /**
     * Retrieve next batch from queue. Blocks if queue is empty.
     * 
     * @return Next RecordBatch from queue
     * @throws InterruptedException If thread interrupted
     */
    public RecordBatch take() throws InterruptedException {
        return queue.take();
    }
    
    /**
     * Retrieve batch with timeout. Returns null if timeout occurs.
     * 
     * @param timeout How long to wait
     * @param unit TimeUnit
     * @return Next batch, or null if timeout
     * @throws InterruptedException If thread interrupted
     */
    public RecordBatch pollWithTimeout(long timeout, TimeUnit unit) 
            throws InterruptedException {
        return queue.poll(timeout, unit);
    }
    
    /**
     * Get queue size (approximate - can change between check and use).
     */
    public int size() {
        return queue.size();
    }
    
    /**
     * Check if queue is empty.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * Check if queue is full.
     */
    public boolean isFull() {
        return queue.size() >= DEFAULT_CAPACITY;
    }
    
    /**
     * Clear all batches from queue.
     */
    public void clear() {
        queue.clear();
    }
}
