package com.tmg.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.tmg.common.ColumnMapper;
import com.tmg.common.ExcelGenerator;
import com.tmg.common.LayoutHeaderReader;
import com.tmg.common.ProdFileParser;
import com.tmg.common.RecordBatch;
import com.tmg.common.RecordQueue;
import com.tmg.threading.VirtualThreadExecutor;
import com.tmg.threading.ThreadSafeExcelGenerator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI Window for Production File Delimiter functionality.
 * Allows users to:
 * 1. Import a production file (fixed-width format)
 * 2. Import a JSON mapping configuration file
 * 3. Import a layout header Excel file
 * 4. Generate an output Excel file from the production data
 * 5. View the generated Excel file
 */
public class ProdFileDelimiterUI extends JFrame {
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(ProdFileDelimiterUI.class);

	private JLabel lblProdFilePath, lblMappingFilePath, lblLayoutHeaderPath;
	private JLabel lblOutputFilePath, lblStatus;
	private JTextArea txtLog;
	private JProgressBar progressBar;
	private JButton btnBrowseProdFile, btnBrowseMappingFile, btnBrowseLayoutHeader;
	private JButton btnGenerate, btnViewFile, btnClear;
	private JFileChooser fileChooser;

	private String selectedProdFilePath = "";
	private String selectedMappingFilePath = "";
	private String selectedLayoutHeaderPath = "";
	private String generatedOutputFilePath = "";

	private static final String DEFAULT_OUTPUT_FOLDER = System.getProperty("user.dir") + "\\Output\\Delimiter_Generated";

	/**
	 * Constructor - initializes and displays the UI.
	 */
	public ProdFileDelimiterUI() {
		initializeUI();
		setVisible(true);
	}

	/**
	 * Initialize all UI components.
	 */
	private void initializeUI() {
		setTitle("Production File Delimiter - File Processing");
		setSize(900, 700);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setResizable(true);

		// Create main panel
		JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		mainPanel.setBackground(Color.WHITE);

		// Create input panel
		JPanel inputPanel = createInputPanel();

		// Create button panel
		JPanel buttonPanel = createButtonPanel();

		// Create log panel
		JPanel logPanel = createLogPanel();

		// Add panels to main panel
		mainPanel.add(inputPanel, BorderLayout.NORTH);
		mainPanel.add(logPanel, BorderLayout.CENTER);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		add(mainPanel);
	}

	/**
	 * Create the input file selection panel.
	 * 
	 * @return input panel with file browse buttons
	 */
	private JPanel createInputPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(new TitledBorder("Input Files"));
		panel.setBackground(Color.WHITE);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// Production File
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.1;
		panel.add(new JLabel("Production File:"), gbc);

		lblProdFilePath = new JLabel("No file selected");
		lblProdFilePath.setForeground(Color.BLUE);
		gbc.gridx = 1;
		gbc.weightx = 0.7;
		panel.add(lblProdFilePath, gbc);

		btnBrowseProdFile = new JButton("Browse");
		btnBrowseProdFile.addActionListener(e -> browseProdFile());
		gbc.gridx = 2;
		gbc.weightx = 0.1;
		panel.add(btnBrowseProdFile, gbc);

		// Mapping File
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weightx = 0.1;
		panel.add(new JLabel("Mapping File (JSON):"), gbc);

		lblMappingFilePath = new JLabel("No file selected");
		lblMappingFilePath.setForeground(Color.BLUE);
		gbc.gridx = 1;
		gbc.weightx = 0.7;
		panel.add(lblMappingFilePath, gbc);

		btnBrowseMappingFile = new JButton("Browse");
		btnBrowseMappingFile.addActionListener(e -> browseMappingFile());
		gbc.gridx = 2;
		gbc.weightx = 0.1;
		panel.add(btnBrowseMappingFile, gbc);

		// Layout Header File
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.weightx = 0.1;
		panel.add(new JLabel("Layout Header (Excel):"), gbc);

		lblLayoutHeaderPath = new JLabel("No file selected");
		lblLayoutHeaderPath.setForeground(Color.BLUE);
		gbc.gridx = 1;
		gbc.weightx = 0.7;
		panel.add(lblLayoutHeaderPath, gbc);

		btnBrowseLayoutHeader = new JButton("Browse");
		btnBrowseLayoutHeader.addActionListener(e -> browseLayoutHeaderFile());
		gbc.gridx = 2;
		gbc.weightx = 0.1;
		panel.add(btnBrowseLayoutHeader, gbc);

		// Output File Path (read-only info)
		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.weightx = 0.1;
		panel.add(new JLabel("Output Location:"), gbc);

		lblOutputFilePath = new JLabel(DEFAULT_OUTPUT_FOLDER);
		lblOutputFilePath.setForeground(new Color(0, 100, 0));
		gbc.gridx = 1;
		gbc.gridwidth = 2;
		gbc.weightx = 0.9;
		panel.add(lblOutputFilePath, gbc);

		return panel;
	}

	/**
	 * Create the button action panel.
	 * 
	 * @return button panel
	 */
	private JPanel createButtonPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		panel.setBackground(Color.WHITE);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// Status label
		lblStatus = new JLabel("Ready");
		lblStatus.setFont(new Font("Tahoma", Font.PLAIN, 11));
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.3;
		panel.add(lblStatus, gbc);

		// Progress bar
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setVisible(false);
		gbc.gridx = 1;
		gbc.weightx = 0.4;
		panel.add(progressBar, gbc);

		// Generate button
		btnGenerate = new JButton("Generate File");
		btnGenerate.setFont(new Font("Tahoma", Font.BOLD, 11));
		btnGenerate.addActionListener(e -> generateFile());
		gbc.gridx = 2;
		gbc.weightx = 0.1;
		panel.add(btnGenerate, gbc);

		// View File button
		btnViewFile = new JButton("View File");
		btnViewFile.setFont(new Font("Tahoma", Font.BOLD, 11));
		btnViewFile.setEnabled(false);
		btnViewFile.addActionListener(e -> viewGeneratedFile());
		gbc.gridx = 3;
		gbc.weightx = 0.1;
		panel.add(btnViewFile, gbc);

		// Clear button
		btnClear = new JButton("Clear");
		btnClear.setFont(new Font("Tahoma", Font.BOLD, 11));
		btnClear.addActionListener(e -> clearAllInputs());
		gbc.gridx = 4;
		gbc.weightx = 0.1;
		panel.add(btnClear, gbc);

		return panel;
	}

	/**
	 * Create the log display panel.
	 * 
	 * @return log panel
	 */
	private JPanel createLogPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new TitledBorder("Processing Log"));
		panel.setBackground(Color.WHITE);

		txtLog = new JTextArea();
		txtLog.setEditable(false);
		txtLog.setFont(new Font("Courier New", Font.PLAIN, 10));
		txtLog.setLineWrap(true);
		txtLog.setWrapStyleWord(true);
		txtLog.setText("Ready for processing...\n");

		JScrollPane scrollPane = new JScrollPane(txtLog);
		scrollPane.setPreferredSize(new Dimension(800, 300));

		panel.add(scrollPane, BorderLayout.CENTER);

		return panel;
	}

	/**
	 * Open file browser for production file selection.
	 */
	private void browseProdFile() {
		fileChooser = new JFileChooser();
		fileChooser.setFileFilter(new FileNameExtensionFilter("Text Files (*.txt, *.TXT)", "txt", "TXT"));

		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			selectedProdFilePath = fileChooser.getSelectedFile().getAbsolutePath();
			lblProdFilePath.setText(selectedProdFilePath);
			appendLog("Production file selected: " + selectedProdFilePath);
		}
	}

	/**
	 * Open file browser for JSON mapping file selection.
	 * Points to ./TMG_Enroll_Legacyn directory and filters for JSON files only.
	 */
	private void browseMappingFile() {
		fileChooser = new JFileChooser();
		
		// Set initial directory to ./ root (where JSON files are stored)
		File initialDir = new File(".");
		if (initialDir.exists() && initialDir.isDirectory()) {
			fileChooser.setCurrentDirectory(initialDir);
		}
		
		// Set file filter to JSON files only
		fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files (*.json)", "json"));

		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			selectedMappingFilePath = fileChooser.getSelectedFile().getAbsolutePath();
			lblMappingFilePath.setText(selectedMappingFilePath);
			appendLog("Mapping file selected: " + selectedMappingFilePath);
		}
	}

	/**
	 * Open file browser for layout header Excel file selection.
	 * Points to ./Layout Templates and filters for Excel files only.
	 */
	private void browseLayoutHeaderFile() {
		fileChooser = new JFileChooser();
		
		// Set initial directory to ./Layout Templates (where Excel files are stored)
		File initialDir = new File("./Layout Templates");
		if (initialDir.exists() && initialDir.isDirectory()) {
			fileChooser.setCurrentDirectory(initialDir);
		}
		
		// Set file filter to Excel files only
		fileChooser.setFileFilter(
				new FileNameExtensionFilter("Excel Files (*.xlsx, *.xls)", "xlsx", "xls", "XLSX", "XLS"));

		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			selectedLayoutHeaderPath = fileChooser.getSelectedFile().getAbsolutePath();
			lblLayoutHeaderPath.setText(selectedLayoutHeaderPath);
			appendLog("Layout header file selected: " + selectedLayoutHeaderPath);
		}
	}

	/**
	 * Validate that all required files are selected.
	 * 
	 * @return true if all files are selected, false otherwise
	 */
	private boolean validateInputs() {
		if (selectedProdFilePath.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Please select a production file.", "Validation Error",
					JOptionPane.ERROR_MESSAGE);
			appendLog("ERROR: Production file not selected");
			return false;
		}

		if (selectedMappingFilePath.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Please select a JSON mapping file.", "Validation Error",
					JOptionPane.ERROR_MESSAGE);
			appendLog("ERROR: Mapping file not selected");
			return false;
		}

		if (selectedLayoutHeaderPath.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Please select a layout header Excel file.", "Validation Error",
					JOptionPane.ERROR_MESSAGE);
			appendLog("ERROR: Layout header file not selected");
			return false;
		}

		// Validate file existence
		if (!new File(selectedProdFilePath).exists()) {
			JOptionPane.showMessageDialog(this, "Production file does not exist.", "Validation Error",
					JOptionPane.ERROR_MESSAGE);
			appendLog("ERROR: Production file does not exist: " + selectedProdFilePath);
			return false;
		}

		if (!new File(selectedMappingFilePath).exists()) {
			JOptionPane.showMessageDialog(this, "Mapping file does not exist.", "Validation Error",
					JOptionPane.ERROR_MESSAGE);
			appendLog("ERROR: Mapping file does not exist: " + selectedMappingFilePath);
			return false;
		}

		if (!new File(selectedLayoutHeaderPath).exists()) {
			JOptionPane.showMessageDialog(this, "Layout header file does not exist.", "Validation Error",
					JOptionPane.ERROR_MESSAGE);
			appendLog("ERROR: Layout header file does not exist: " + selectedLayoutHeaderPath);
			return false;
		}

		return true;
	}

	/**
	 * Initiate file generation process.
	 * This runs in a background thread to prevent UI freezing.
	 */
	private void generateFile() {
		if (!validateInputs()) {
			return;
		}

		btnGenerate.setEnabled(false);
		btnViewFile.setEnabled(false);

		SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
			@Override
			protected Boolean doInBackground() throws Exception {
				try {
					return performFileGeneration();
				} catch (Exception e) {
					publish("ERROR: " + e.getMessage());
					LOG.error("Error in file generation: ", e);
					return false;
				}
			}

			@Override
			protected void process(List<String> chunks) {
				for (String message : chunks) {
					appendLog(message);
				}
			}

			@Override
			protected void done() {
				btnGenerate.setEnabled(true);
				try {
					if (get()) {
						lblStatus.setText("Generation Successful!");
						lblStatus.setForeground(new Color(0, 100, 0));
						btnViewFile.setEnabled(true);
						JOptionPane.showMessageDialog(ProdFileDelimiterUI.this,
								"File generated successfully!\nLocation: " + generatedOutputFilePath, "Success",
								JOptionPane.INFORMATION_MESSAGE);
					} else {
						lblStatus.setText("Generation Failed!");
						lblStatus.setForeground(Color.RED);
						JOptionPane.showMessageDialog(ProdFileDelimiterUI.this,
								"File generation failed. Check the log for details.", "Error",
								JOptionPane.ERROR_MESSAGE);
					}
				} catch (Exception e) {
					lblStatus.setText("Generation Failed!");
					lblStatus.setForeground(Color.RED);
					LOG.error("Error in generation completion: ", e);
				}
			}
		};

		lblStatus.setText("Processing...");
		lblStatus.setForeground(Color.BLUE);
		progressBar.setVisible(true);
		progressBar.setIndeterminate(true);
		worker.execute();
	}

	/**
	 * Perform the actual file generation logic.
	 * 
	 * @return true if successful, false otherwise
	 */
	private boolean performFileGeneration() {
		appendLog("========================================");
		appendLog("Starting MULTI-THREADED file generation...");
		appendLog("Prod File: " + selectedProdFilePath);
		appendLog("Mapping File: " + selectedMappingFilePath);
		appendLog("Layout Header: " + selectedLayoutHeaderPath);
		appendLog("========================================");
		
		// MEMORY DIAGNOSTICS: Log initial memory state
		long memInitial = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		long maxMemory = Runtime.getRuntime().maxMemory();
		appendLog("[MEMORY] Initial heap: " + (memInitial / 1024 / 1024) + " MB / Max: " + (maxMemory / 1024 / 1024) + " MB");

		// Declare variables outside try block
		ThreadSafeExcelGenerator excel = null;  // Mutable field, assigned in try block
		final String[] jsonFieldHeadings;
		final String[] excelHeaders;
		final RecordQueue parseQueue;
		final RecordQueue mapQueue;
		final CountDownLatch parseComplete;
		final CountDownLatch mapComplete;
		final ProdFileParser parser;
		final int BATCH_SIZE = 50;  // FURTHER REDUCED from 250: Only 50 records per batch (7.5 MB)
		final int NUM_MAP_THREADS = 12;  // INCREASED from 8: 12 threads to consume batches faster

		try {
			// VALIDATION PHASE: Check all prerequisites, throw exceptions on failure
			
			// Step 1: Load and validate mapping configuration
			appendLog("[1/6] Loading mapping configuration...");
			ProdFileParser tempParser = new ProdFileParser(selectedProdFilePath, selectedMappingFilePath);
			if (!tempParser.loadMapping()) {
				throw new Exception("Failed to load mapping from JSON file");
			}
			String[] tempJsonHeadings = tempParser.getFieldHeadings();
			appendLog("[1/6] Mapping loaded with " + tempJsonHeadings.length + " fields");

			// Step 2: Load and validate headers
			appendLog("[2/6] Reading headers from layout Excel file...");
			LayoutHeaderReader layoutReader = new LayoutHeaderReader(selectedLayoutHeaderPath);
			String[] tempExcelHeaders;
			try {
				tempExcelHeaders = layoutReader.readHeaders();
				appendLog("[2/6] Read " + tempExcelHeaders.length + " column headers from Excel");
				boolean allEmpty = true;
				for (String header : tempExcelHeaders) {
					if (!header.isEmpty()) {
						allEmpty = false;
						break;
					}
				}
				if (allEmpty) {
					throw new Exception("All headers from Excel are empty!");
				}
			} catch (IOException e) {
				throw new Exception("Failed to read headers - " + e.getMessage(), e);
			}

			// INITIALIZATION PHASE: Now that validation is complete, initialize final blank fields
			parser = tempParser;
			jsonFieldHeadings = tempJsonHeadings;
			excelHeaders = tempExcelHeaders;
			
// Initialize queues - REDUCED CAPACITY to prevent memory buildup
		parseQueue = new RecordQueue(5);  // REDUCED from 20: 5 × 30 MB = ~150 MB max (not 2.4 GB)
		mapQueue = new RecordQueue(5);
			
			// Initialize CountDownLatches
			parseComplete = new CountDownLatch(1);
			mapComplete = new CountDownLatch(NUM_MAP_THREADS);

			// Step 6: INITIALIZE EXCEL GENERATOR (MUST BE FINAL FOR LAMBDA USE)
			appendLog("[6/6] Initializing thread-safe Excel generator...");
			String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			String outputFileName = "Delimiter_Output_" + timestamp + ".xlsx";
			String outputFilePath = System.getProperty("user.dir") + "\\Output\\Delimiter_Generated" + File.separator + outputFileName;
			excel = new ThreadSafeExcelGenerator(outputFilePath);
			excel.writeHeaders(excelHeaders);
			appendLog("[6/6] Excel file initialized and headers written");

			// Step 3: PARALLEL PARSING with Virtual Threads
			appendLog("[3/6] Starting PARALLEL PARSING (Virtual Threads)...");
			long fileSize = new File(selectedProdFilePath).length();

			appendLog("[3/6] File size: " + (fileSize / (1024 * 1024)) + " MB");
			appendLog("[3/6] Batch size: " + BATCH_SIZE + " records");
			appendLog("[3/6] Carrier threads: " + Runtime.getRuntime().availableProcessors() + " (CPU cores)");

			// Create virtual thread executor for parsing
			VirtualThreadExecutor parseExecutor = new VirtualThreadExecutor();
			
			// Create local final captures for blank final fields to use in parser lambda
			final RecordQueue parseQueueParser = parseQueue;
			final CountDownLatch parseCompleteParser = parseComplete;
			final ProdFileParser parserLocal = parser;
			
			// Parse in virtual thread and emit batches
			parseExecutor.submit(() -> {
				try {
					try (Stream<RecordBatch> batchStream = parserLocal.parseRecordsAsStream(BATCH_SIZE)) {
						batchStream.forEach(batch -> {
							try {
								parseQueueParser.put(batch);
								appendLog("[3/6] Batch " + batch.getBatchIndex() + "/" + batch.getTotalBatches()
										+ " parsed (" + batch.getRecordCount() + " records)");
							} catch (InterruptedException e) {
								LOG.error("Error putting batch in queue", e);
								Thread.currentThread().interrupt();
							}
						});
					}
					appendLog("[3/6] Parsing complete!");
				} catch (Exception e) {
					LOG.error("Error during parsing: " + e.getMessage(), e);
					appendLog("ERROR: Failed to parse production file - " + e.getMessage());
				} finally {
					parseCompleteParser.countDown();
				}
			});

			// Step 5: PARALLEL MAPPING with Virtual Threads (4 threads)
			appendLog("[5/6] Starting PARALLEL MAPPING with " + NUM_MAP_THREADS + " VIRTUAL threads...");

			VirtualThreadExecutor mapExecutor = new VirtualThreadExecutor();
			
			// Create local final captures for all blank final fields to use in lambdas
			final ThreadSafeExcelGenerator excelWriter = excel;
			final RecordQueue parseQueueCapture = parseQueue;
			final String[] excelHeadersCapture = excelHeaders;
			final String[] jsonFieldHeadingsCapture = jsonFieldHeadings;
			final CountDownLatch parseCompleteCapture = parseComplete;
			final CountDownLatch mapCompleteCapture = mapComplete;

			for (int i = 0; i < NUM_MAP_THREADS; i++) {
				final int threadNum = i;
				mapExecutor.submit(() -> {
					try {
						while (true) {
							// Read from parseQueue (where parser puts batches)
							RecordBatch batch = parseQueueCapture.pollWithTimeout(5, TimeUnit.SECONDS);
							if (batch == null) {
								// Check if parsing is still running
								if (parseCompleteCapture.getCount() == 0 && parseQueueCapture.isEmpty()) {
									break;
								}
								continue;
						}

						// Map records
						long memBeforeMap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
						List<Map<String, String>> mappedRecords = ColumnMapper
								.mapRecordsToColumns(batch.getRecords(), excelHeadersCapture, jsonFieldHeadingsCapture);
						long memAfterMap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

						// Create mapped batch with same indices
						RecordBatch mappedBatch = new RecordBatch(mappedRecords, batch.getBatchIndex(),
								batch.getTotalBatches());

						try {
							// MEMORY DIAGNOSTIC: Log memory usage at each stage
							long memBeforeWrite = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
							
							// Write batch to Excel (thread-safe with locks)
							excelWriter.writeBatch(mappedBatch, excelHeadersCapture);
							long memAfterWrite = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
							
							appendLog("[5/6] Virtual-T" + threadNum + " batch " + batch.getBatchIndex() 
									+ " mapped/wrote " + mappedRecords.size() + " records"
									+ " [MEM: Map=" + ((memAfterMap - memBeforeMap) / 1024 / 1024) + "MB"
									+ " Write=" + ((memAfterWrite - memBeforeWrite) / 1024 / 1024) + "MB"
									+ " Heap=" + (memAfterWrite / 1024 / 1024) + "MB]");
							
							// Update progress
							int progress = Math.min((excelWriter.getRowCount() * 100) / 12548, 95);
							updateProgress(progress);
							
							// MEMORY OPTIMIZATION: Explicitly hint garbage collection to free batch memory immediately
							// This prevents memory buildup when parser is faster than mapper threads
							mappedRecords.clear();
							System.gc();
						} catch (Exception e) {
							LOG.error("Error writing batch: " + e.getMessage(), e);
							appendLog("ERROR: Failed to write batch - " + e.getMessage());
						}
					}
				} catch (InterruptedException e) {
					LOG.error("Virtual mapper thread interrupted", e);
					Thread.currentThread().interrupt();
				} finally {
					mapCompleteCapture.countDown();
				}
			});
			}

			// Wait for parsing to complete
			appendLog("[3/6] Waiting for parsing virtual thread to complete...");
			boolean parseFinished = parseComplete.await(15, TimeUnit.MINUTES);
			if (parseFinished) {
				appendLog("[3/6] Parsing virtual thread finished successfully");
			} else {
				appendLog("WARNING: Parsing did not complete in time");
			}

			// Wait for all mapping virtual threads to complete
			appendLog("[5/6] Waiting for " + NUM_MAP_THREADS + " mapping virtual threads to complete...");
			boolean mapFinished = mapComplete.await(15, TimeUnit.MINUTES);
			if (mapFinished) {
				appendLog("[5/6] All " + NUM_MAP_THREADS + " mapping virtual threads finished");
			} else {
				appendLog("WARNING: Mapping threads did not complete in time");
			}

			// Shutdown executors
			try {
				parseExecutor.shutdown(1, TimeUnit.MINUTES);
				mapExecutor.shutdown(1, TimeUnit.MINUTES);
				appendLog("[5/6] Virtual thread executors shutdown complete");
			} catch (InterruptedException e) {
				LOG.error("Error shutting down executors", e);
				appendLog("WARNING: Error during executor shutdown");
			}
			// Save and close Excel file
			appendLog("[6/6] Saving Excel file...");
			excel.save();
			generatedOutputFilePath = outputFilePath;

			appendLog("[6/6] Excel file saved successfully");
			appendLog("Output: " + generatedOutputFilePath);
			appendLog("Total Rows Written: " + excel.getRowCount());
			appendLog("Total Columns: " + excelHeaders.length);
			appendLog("========================================");
			appendLog("✅ Generation completed successfully with MULTI-THREADING!");
			appendLog("========================================");

			updateProgress(100);
			return true;

		} catch (IOException e) {
			appendLog("ERROR: IOException - " + e.getMessage());
			LOG.error("IO Error during file generation", e);
			return false;
		} catch (InterruptedException e) {
			appendLog("ERROR: Process interrupted - " + e.getMessage());
			LOG.error("Thread interrupted", e);
			return false;
		} catch (Exception e) {
			appendLog("ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
			LOG.error("Unexpected error during file generation", e);
			return false;
		} finally {
			// Cleanup - excel is a nullable field that may be null if exception occurred before initialization
			try {
				if (excel != null) {
					excel.close();  // Critical for streaming mode: cleanup temp files
				}
			} catch (Exception e) {
				LOG.error("Error closing Excel generator during cleanup", e);
			}
			progressBar.setVisible(false);
		}
	}

	/**
	 * Open the generated Excel file using the default application.
	 */
	private void viewGeneratedFile() {
		if (generatedOutputFilePath.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No file generated yet. Please generate a file first.", "Warning",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		File outputFile = new File(generatedOutputFilePath);
		if (!outputFile.exists()) {
			JOptionPane.showMessageDialog(this, "Generated file not found: " + generatedOutputFilePath, "Error",
					JOptionPane.ERROR_MESSAGE);
			appendLog("ERROR: File not found: " + generatedOutputFilePath);
			return;
		}

		try {
			Desktop.getDesktop().open(outputFile);
			appendLog("Opened file: " + generatedOutputFilePath);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Unable to open file. " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
			appendLog("ERROR: Unable to open file - " + e.getMessage());
			LOG.error("Error opening generated file: ", e);
		}
	}

	/**
	 * Clear all input selections and reset the UI.
	 */
	private void clearAllInputs() {
		selectedProdFilePath = "";
		selectedMappingFilePath = "";
		selectedLayoutHeaderPath = "";
		generatedOutputFilePath = "";

		lblProdFilePath.setText("No file selected");
		lblMappingFilePath.setText("No file selected");
		lblLayoutHeaderPath.setText("No file selected");
		lblStatus.setText("Ready");
		lblStatus.setForeground(Color.BLACK);

		btnViewFile.setEnabled(false);

		txtLog.setText("Ready for processing...\n");

		appendLog("All inputs cleared");
	}

	/**
	 * Append a message to the log text area.
	 * 
	 * @param message message to append
	 */
	private void appendLog(String message) {
		SwingUtilities.invokeLater(() -> {
			String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
			txtLog.append("[" + timestamp + "] " + message + "\n");
			txtLog.setCaretPosition(txtLog.getDocument().getLength());
		});
	}

	/**
	 * Update progress bar value (thread-safe).
	 * 
	 * @param value percentage (0-100)
	 */
	private void updateProgress(int value) {
		SwingUtilities.invokeLater(() -> {
			if (value >= 0 && value <= 100) {
				progressBar.setValue(value);
				progressBar.setVisible(true);
			}
		});
	}

	/**
	 * Show this window in the UI.
	 * Creates a new instance if it doesn't exist.
	 */
	public static void showWindow() {
		SwingUtilities.invokeLater(() -> {
			new ProdFileDelimiterUI();
		});
	}
}
