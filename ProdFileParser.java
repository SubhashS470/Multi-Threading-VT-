package com.tmg.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmg.beans.FixedWidthField;
import com.tmg.beans.FixedWidthMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for parsing fixed-width production files and JSON mapping configurations.
 * Handles extraction of data from fixed-width records based on mapping definitions.
 */
public class ProdFileParser {
	private static final Logger LOG = LoggerFactory.getLogger(ProdFileParser.class);

	private FixedWidthMapping mapping;
	private File productionFile;
	private File mappingFile;

	/**
	 * Constructor to initialize parser with production and mapping files.
	 * 
	 * @param productionFilePath path to the production file
	 * @param mappingFilePath    path to the JSON mapping file
	 */
	public ProdFileParser(String productionFilePath, String mappingFilePath) {
		this.productionFile = new File(productionFilePath);
		this.mappingFile = new File(mappingFilePath);
	}

	/**
	 * Load and parse the JSON mapping configuration file.
	 * 
	 * @return true if successful, false otherwise
	 */
	public boolean loadMapping() {
		try {
			if (!mappingFile.exists()) {
				LOG.error("Mapping file not found: {}", mappingFile.getAbsolutePath());
				return false;
			}

			ObjectMapper objectMapper = new ObjectMapper();
			mapping = objectMapper.readValue(mappingFile, FixedWidthMapping.class);

			if (mapping == null || !mapping.isValid()) {
				LOG.error("Mapping is invalid or empty");
				return false;
			}

			LOG.info("Successfully loaded mapping with {} fields", mapping.getFieldCount());
			return true;
		} catch (IOException e) {
			LOG.error("Error loading mapping file: {}", e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Parse all records from the production file using the loaded mapping.
	 * 
	 * @return list of maps where each map represents a record with column names as keys
	 * @throws IOException if file cannot be read
	 */
	public List<Map<String, String>> parseRecords() throws IOException {
		List<Map<String, String>> records = new ArrayList<>();

		if (mapping == null || !mapping.isValid()) {
			LOG.error("Mapping not loaded or invalid. Call loadMapping() first.");
			throw new IllegalStateException("Mapping must be loaded before parsing records");
		}

		if (!productionFile.exists()) {
			LOG.error("Production file not found: {}", productionFile.getAbsolutePath());
			throw new IOException("Production file not found: " + productionFile.getAbsolutePath());
		}

		List<FixedWidthField> fields = mapping.getFields();
		LOG.debug("Production file will be parsed using {} fields", fields.size());

		try (BufferedReader reader = new BufferedReader(new FileReader(productionFile))) {
			String line;
			int lineNumber = 0;
			int recordCount = 0;

			while ((line = reader.readLine()) != null) {
				lineNumber++;

				// Skip empty lines
				if (line.trim().isEmpty()) {
					LOG.debug("Line {}: Skipping empty line", lineNumber);
					continue;
				}

				LOG.debug("Line {}: Processing production file line (length: {})", lineNumber, line.length());
				LOG.debug("  Line content (first 100 chars): {}", 
					line.length() > 100 ? line.substring(0, 100) : line);

				Map<String, String> record = new LinkedHashMap<>();

				// Extract each field from the line
				for (int i = 0; i < fields.size(); i++) {
					FixedWidthField field = fields.get(i);
					String value = field.extractValue(line);
					
					if (i < 10) { // Log first 10 fields for debugging
						LOG.debug("  Field {}: '{}' = '{}' (extracted from positions {}-{})",
							i, field.getHeading(), value,
							field.getIndexFrom(), field.getIndexTo());
					}
					
					record.put(field.getHeading(), value);
				}

				recordCount++;
				if (recordCount <= 3) { // Log first 3 records in detail
					LOG.debug("Record {}: Extracted {} fields", recordCount, record.size());
					int fieldNum = 0;
					for (Map.Entry<String, String> entry : record.entrySet()) {
						if (fieldNum < 10) { // Show first 10 fields
							String displayValue = entry.getValue().length() > 50
								? entry.getValue().substring(0, 50) + "..."
								: entry.getValue();
							LOG.debug("  [{}] '{}' = '{}'", fieldNum, entry.getKey(), displayValue);
						}
						fieldNum++;
					}
					if (record.size() > 10) {
						LOG.debug("  ... and {} more fields", record.size() - 10);
					}
				}

				records.add(record);
			}

			LOG.info("Successfully parsed {} records from production file ({} total lines read)", 
				records.size(), lineNumber);
			return records;
		} catch (IOException e) {
			LOG.error("Error parsing production file: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Get all field headings in order.
	 * 
	 * @return array of field headings, or empty array if mapping not loaded
	 */
	public String[] getFieldHeadings() {
		if (mapping == null) {
			return new String[0];
		}
		return mapping.getFieldHeadings();
	}

	/**
	 * Get the loaded mapping object.
	 * 
	 * @return FixedWidthMapping object, or null if not loaded
	 */
	public FixedWidthMapping getMapping() {
		return mapping;
	}

	/**
	 * Validate that required files exist.
	 * 
	 * @return true if both files exist, false otherwise
	 */
	public boolean validateFiles() {
		boolean prodFileExists = productionFile.exists();
		boolean mappingFileExists = mappingFile.exists();

		if (!prodFileExists) {
			LOG.error("Production file not found: {}", productionFile.getAbsolutePath());
		}
		if (!mappingFileExists) {
			LOG.error("Mapping file not found: {}", mappingFile.getAbsolutePath());
		}

		return prodFileExists && mappingFileExists;
	}

	/**
	 * Get the production file path.
	 * 
	 * @return production file path
	 */
	public String getProductionFilePath() {
		return productionFile.getAbsolutePath();
	}

	/**
	 * Get the mapping file path.
	 * 
	 * @return mapping file path
	 */
	public String getMappingFilePath() {
		return mappingFile.getAbsolutePath();
	}

	/**
	 * Parse production file and emit records as batches via Stream.
	 * 
	 * Purpose: Memory-efficient streaming alternative to parseRecords().
	 * Instead of loading 12,547 records (~1.3GB) at once, emits ~1000 records per batch.
	 * 
	 * Behavior:
	 * - Creates Stream that reads file line-by-line
	 * - Accumulates records into batches
	 * - Emits RecordBatch when full (default 1000 records)
	 * - Automatically closes file when stream closes
	 * 
	 * Memory Impact: 1.3GB (all records) → ~100-120MB per batch
	 * 
	 * Usage:
	 *   try (Stream<RecordBatch> batches = parser.parseRecordsAsStream(1000)) {
	 *       batches.forEach(batch -> {
	 *           System.out.println("Processing batch: " + batch);
	 *           // Process batch...
	 *       });
	 *   }
	 * 
	 * @param batchSize Number of records per batch (default: 1000)
	 * @return Stream of RecordBatch objects
	 */
	public Stream<RecordBatch> parseRecordsAsStream(int batchSize) {
		if (mapping == null || !mapping.isValid()) {
			throw new IllegalStateException("Mapping must be loaded before parsing records");
		}
		
		if (!productionFile.exists()) {
			throw new IllegalStateException("Production file not found: " + productionFile.getAbsolutePath());
		}

		BatchIterator batchIterator = new BatchIterator(batchSize);
		Spliterator<RecordBatch> spliterator = Spliterators.spliteratorUnknownSize(
			batchIterator,
			Spliterator.ORDERED | Spliterator.NONNULL
		);
		return StreamSupport.stream(spliterator, false)
			.onClose(batchIterator::close);
	}

	/**
	 * Inner class that implements Iterator pattern for reading batches.
	 * 
	 * Algorithm:
	 * 1. Open BufferedReader on production file
	 * 2. Accumulate records until batch reaches batchSize
	 * 3. When full, return completed RecordBatch
	 * 4. Continue with next batch until EOF
	 * 5. Close file when stream terminates
	 * 
	 * Thread Safety: Single-threaded only (iterator invariant)
	 */
	private class BatchIterator implements Iterator<RecordBatch>, AutoCloseable {
		
		private final int batchSize;
		private BufferedReader reader;
		private String nextLine;
		private boolean hasNextLine;
		private int batchIndex;
		private int totalRecordsRead;
		private final List<FixedWidthField> fields;
		private long totalBatches; // Calculated lazily based on file size
		
		/**
		 * Constructor. Opens file and reads first line.
		 */
		BatchIterator(int batchSize) {
			this.batchSize = batchSize;
			this.batchIndex = 0;
			this.totalRecordsRead = 0;
			this.fields = mapping.getFields();
			this.totalBatches = -1; // Unknown until we calculate
			
			try {
				this.reader = new BufferedReader(new FileReader(productionFile));
				this.nextLine = reader.readLine();
				this.hasNextLine = (nextLine != null);
			} catch (IOException e) {
				LOG.error("Error opening production file for streaming: {}", e.getMessage(), e);
				throw new RuntimeException("Cannot open production file: " + e.getMessage(), e);
			}
		}
		
		/**
		 * Check if more batches are available.
		 */
		@Override
		public boolean hasNext() {
			return hasNextLine;
		}
		
		/**
		 * Get next batch of records.
		 */
		@Override
		public RecordBatch next() {
			List<Map<String, String>> batchRecords = new ArrayList<>(batchSize);
			
			try {
				// Accumulate records until batch is full or EOF
				while (batchRecords.size() < batchSize && hasNextLine) {
					if (nextLine.trim().isEmpty()) {
						// Skip empty lines
						nextLine = reader.readLine();
						hasNextLine = (nextLine != null);
						continue;
					}
					
					// Parse record from fixed-width line
					Map<String, String> record = new LinkedHashMap<>();
					for (FixedWidthField field : fields) {
						String value = field.extractValue(nextLine);
						record.put(field.getHeading(), value);
					}
					
					batchRecords.add(record);
					totalRecordsRead++;
					
					// Read next line
					nextLine = reader.readLine();
					hasNextLine = (nextLine != null);
				}
				
				batchIndex++;
				
				// Estimate total batches (only on first batch)
				if (totalBatches == -1) {
					totalBatches = (long) Math.ceil((double) productionFile.length() 
						/ (1024 * 100)); // Rough estimate: assume ~100KB per 1000 records
				}
				
				RecordBatch batch = new RecordBatch(batchRecords, batchIndex, totalBatches);
				LOG.debug("Created batch: {} with {} records (total read so far: {})",
					batchIndex, batchRecords.size(), totalRecordsRead);
				
				return batch;
				
			} catch (IOException e) {
				LOG.error("Error reading production file at record {}: {}", totalRecordsRead, e.getMessage(), e);
				throw new RuntimeException("Error reading batch: " + e.getMessage(), e);
			}
		}
		
		/**
		 * Close the file reader.
		 */
		@Override
		public void close() {
			if (reader != null) {
				try {
					reader.close();
					LOG.debug("Closed production file reader after reading {} records in {} batches",
						totalRecordsRead, batchIndex);
				} catch (IOException e) {
					LOG.error("Error closing production file reader: {}", e.getMessage(), e);
				}
			}
		}
	}
}
