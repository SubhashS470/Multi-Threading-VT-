package com.tmg.threading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper around Java 21 Virtual Threads for parallel task execution.
 * 
 * Purpose: Simplify virtual thread management and provide progress tracking.
 * 
 * Virtual Threads (Project Loom):
 * - Lightweight threads (100 KB each vs 1.5 MB for OS threads)
 * - Unlimited concurrency (can create 100,000+ threads)
 * - No manual thread pool sizing needed
 * - Automatic kernel thread pooling under the hood
 */
public class VirtualThreadExecutor implements AutoCloseable {
    
    private static final Logger LOG = LoggerFactory.getLogger(VirtualThreadExecutor.class);
    
    private final ExecutorService executor;
    private final AtomicInteger submittedTasks;
    private final AtomicInteger completedTasks;
    private volatile boolean shutdown = false;
    
    /**
     * Create virtual thread executor with default settings.
     */
    public VirtualThreadExecutor() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.submittedTasks = new AtomicInteger(0);
        this.completedTasks = new AtomicInteger(0);
        LOG.info("VirtualThreadExecutor initialized with newVirtualThreadPerTaskExecutor");
    }
    
    /**
     * Submit a task to be executed in a virtual thread.
     * 
     * @param task Runnable (the code to execute)
     * @throws IllegalStateException If executor is already shutdown
     */
    public void submit(Runnable task) {
        if (shutdown) {
            throw new IllegalStateException("Executor is shutdown");
        }
        
        submittedTasks.incrementAndGet();
        
        executor.submit(() -> {
            try {
                task.run();
            } finally {
                completedTasks.incrementAndGet();
            }
        });
    }
    
    /**
     * Submit task with error handling.
     * 
     * @param task Runnable to execute
     * @param errorHandler Consumer that receives exception if thrown
     */
    public void submitWithErrorHandler(
            Runnable task,
            java.util.function.Consumer<Exception> errorHandler) {
        submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                errorHandler.accept(e);
            }
        });
    }
    
    /**
     * Get number of submitted tasks.
     */
    public int getSubmittedCount() {
        return submittedTasks.get();
    }
    
    /**
     * Get number of completed tasks.
     */
    public int getCompletedCount() {
        return completedTasks.get();
    }
    
    /**
     * Get number of pending tasks (not yet completed).
     */
    public int getPendingCount() {
        return submittedTasks.get() - completedTasks.get();
    }
    
    /**
     * Get progress as percentage (0-100).
     */
    public int getProgressPercentage() {
        int submitted = submittedTasks.get();
        if (submitted == 0) return 0;
        return (completedTasks.get() * 100) / submitted;
    }
    
    /**
     * Shutdown executor and wait for all tasks to complete.
     * 
     * @param timeout How long to wait for task completion
     * @param unit TimeUnit (SECONDS, MINUTES, etc)
     * @return true if all tasks completed, false if timeout
     * @throws InterruptedException If current thread interrupted
     */
    public boolean shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        if (shutdown) {
            return true;
        }
        
        shutdown = true;
        executor.shutdown();
        
        boolean completed = executor.awaitTermination(timeout, unit);
        
        if (!completed) {
            LOG.warn("Executor did not terminate within {} {}", timeout, unit);
            executor.shutdownNow();
        } else {
            LOG.info("Executor shutdown complete. Completed: {}/{} tasks",
                completedTasks.get(), submittedTasks.get());
        }
        
        return completed;
    }
    
    /**
     * Graceful shutdown (from AutoCloseable interface).
     */
    @Override
    public void close() throws Exception {
        shutdown(Long.MAX_VALUE, TimeUnit.DAYS);
    }
}
