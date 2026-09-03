package com.tmg.common;

import java.util.List;
import java.util.Map;

/**
 * Immutable container for a batch of records.
 * 
 * Purpose: Represents a chunk of N records from the production file.
 * Enables streaming processing without accumulating all data in memory.
 * 
 * Key Characteristics:
 * - Immutable: Once created, never modified (thread-safe)
 * - Lightweight: Just wraps a list + 2 ints
 * - Progressive: Tracks batch position (e.g., 7 of 13)
 * 
 * Example Usage:
 *   RecordBatch batch = new RecordBatch(recordsList, 1, 13);
 *   System.out.println(batch.getBatchIndex());  // Output: 1
 *   System.out.println(batch.getTotalBatches()); // Output: 13
 *   System.out.println(batch.getProgressPercentage()); // Output: 7%
 */
public class RecordBatch {
    
    private final List<Map<String, String>> records;
    private final int batchIndex;
    private final long totalBatches;
    
    /**
     * Constructor to create a RecordBatch.
     * 
     * @param records      List of maps representing records (typically 1000)
     * @param batchIndex   Current batch number (1, 2, 3, ...)
     * @param totalBatches Total batches to be created
     */
    public RecordBatch(
            List<Map<String, String>> records,
            int batchIndex,
            long totalBatches) {
        this.records = records;
        this.batchIndex = batchIndex;
        this.totalBatches = totalBatches;
    }
    
    /**
     * Get the list of records in this batch.
     */
    public List<Map<String, String>> getRecords() {
        return records;
    }
    
    /**
     * Get the batch index (sequential number).
     */
    public int getBatchIndex() {
        return batchIndex;
    }
    
    /**
     * Get total number of batches.
     */
    public long getTotalBatches() {
        return totalBatches;
    }
    
    /**
     * Calculate progress as percentage (0-100).
     * Example: Batch 7 of 13 = 53%
     */
    public int getProgressPercentage() {
        return (int) ((batchIndex * 100) / totalBatches);
    }
    
    /**
     * Get number of records in this batch.
     */
    public int getRecordCount() {
        return records.size();
    }
    
    /**
     * String representation for logging.
     */
    @Override
    public String toString() {
        return String.format(
            "RecordBatch[%d/%d, %d records, %d%%]",
            batchIndex,
            totalBatches,
            records.size(),
            getProgressPercentage()
        );
    }
}
