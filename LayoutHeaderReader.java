package com.tmg.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for reading headers from the Layout Header Excel file.
 * Extracts column headers from the first row of the first sheet.
 */
public class LayoutHeaderReader {
	private static final Logger LOG = LoggerFactory.getLogger(LayoutHeaderReader.class);

	private File layoutFile;

	/**
	 * Constructor to initialize with layout header file path.
	 * 
	 * @param layoutFilePath path to the layout header Excel file
	 */
	public LayoutHeaderReader(String layoutFilePath) {
		this.layoutFile = new File(layoutFilePath);
	}

	/**
	 * Read all headers from the first row of the first sheet in the Excel file.
	 * Returns headers in the order they appear in the Excel file.
	 * Automatically detects if headers are in row 0 or row 1.
	 * 
	 * @return array of header strings, or empty array if file not found
	 * @throws IOException if file cannot be read
	 */
	public String[] readHeaders() throws IOException {
		List<String> headers = new ArrayList<>();

		if (!layoutFile.exists()) {
			LOG.error("Layout header file not found: {}", layoutFile.getAbsolutePath());
			throw new IOException("Layout header file not found: " + layoutFile.getAbsolutePath());
		}

		try (FileInputStream fis = new FileInputStream(layoutFile);
				Workbook workbook = WorkbookFactory.create(fis)) {

			// Get the first sheet
			Sheet sheet = workbook.getSheetAt(0);
			if (sheet == null) {
				LOG.error("No sheet found in layout header file");
				throw new IOException("No sheet found in layout header file");
			}

			// Try to find the header row
			// Strategy: Check row 0 first, if it looks like data, try row 1
			Row headerRow = null;
			int headerRowNum = 0;
			
			LOG.info("Scanning for header row in Excel layout file...");
			
			// Check row 0
			Row row0 = sheet.getRow(0);
			if (row0 != null) {
				LOG.debug("Row 0 found with {} cells", row0.getLastCellNum());
				
				// Check if row 0 looks like headers or data
				boolean row0IsData = isRowData(row0);
				LOG.debug("Row 0 appears to be: {}", row0IsData ? "DATA" : "HEADERS");
				
				if (!row0IsData) {
					headerRow = row0;
					headerRowNum = 0;
					LOG.info("Using Row 0 as header row");
				} else {
					// Row 0 looks like data, try row 1
					Row row1 = sheet.getRow(1);
					if (row1 != null) {
						LOG.debug("Row 1 found with {} cells", row1.getLastCellNum());
						boolean row1IsData = isRowData(row1);
						LOG.debug("Row 1 appears to be: {}", row1IsData ? "DATA" : "HEADERS");
						
						if (!row1IsData) {
							headerRow = row1;
							headerRowNum = 1;
							LOG.info("Using Row 1 as header row (Row 0 was data)");
						} else {
							// Both look like data, use row 1 anyway
							headerRow = row1;
							headerRowNum = 1;
							LOG.warn("Both rows look like data. Using Row 1 as headers.");
						}
					} else {
						// No row 1, use row 0
						headerRow = row0;
						headerRowNum = 0;
						LOG.warn("Row 1 not found. Using Row 0 as headers.");
					}
				}
			} else {
				// Row 0 is null, try row 1
				Row row1 = sheet.getRow(1);
				if (row1 != null) {
					headerRow = row1;
					headerRowNum = 1;
					LOG.warn("Row 0 is empty. Using Row 1 as header row.");
				} else {
					LOG.error("No data rows found in layout header file");
					throw new IOException("No data rows found in layout header file");
				}
			}

			if (headerRow == null) {
				LOG.error("Failed to find header row");
				throw new IOException("Failed to find header row in layout file");
			}

			// Extract all cells from the header row
			int lastCellNum = headerRow.getLastCellNum();
			LOG.info("Reading {} headers from Row {} of layout file", lastCellNum, headerRowNum);

			for (int i = 0; i < lastCellNum; i++) {
				Cell cell = headerRow.getCell(i);
				String headerValue = "";

				if (cell != null) {
					// Get cell value as string, regardless of cell type
					headerValue = getCellValueAsString(cell);
					// IMPORTANT: Trim whitespace from headers
					// Excel headers may have leading/trailing spaces that don't match JSON field names
					headerValue = headerValue.trim();
				}

				headers.add(headerValue);
				LOG.debug("Header[{}]: '{}'", i, headerValue);
			}

			LOG.info("Successfully read {} headers from layout file (Row {})", headers.size(), headerRowNum);
			return headers.toArray(new String[0]);

		} catch (IOException e) {
			LOG.error("Error reading layout header file: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Heuristic to determine if a row contains data or headers.
	 * 
	 * Strategy:
	 * 1. Headers typically contain spaces and dictionary words
	 * 2. Data values like "AEMEMB15" or "281850006" don't usually contain spaces
	 * 3. Also check if cells contain common header keywords
	 * 
	 * @param row the row to check
	 * @return true if row appears to be data, false if it appears to be headers
	 */
	private boolean isRowData(Row row) {
		if (row == null || row.getLastCellNum() == 0) {
			return false; // Empty row is not data
		}

		// Count cells with spaces (headers usually have spaces) vs cells without
		int cellsWithSpaces = 0;
		int cellsWithoutSpaces = 0;
		int cellsChecked = 0;

		for (int i = 0; i < row.getLastCellNum() && cellsChecked < 9; i++) {
			Cell cell = row.getCell(i);
			if (cell != null && cell.getCellType() != null) {
				String cellValue = "";
				
				switch (cell.getCellType()) {
				case STRING:
					cellValue = cell.getStringCellValue().trim();
					break;
				case NUMERIC:
					cellValue = String.valueOf(cell.getNumericCellValue());
					break;
				default:
					continue;
				}

				if (!cellValue.isEmpty()) {
					cellsChecked++;
					
					// Check if cell contains space
					if (cellValue.contains(" ")) {
						cellsWithSpaces++;
						LOG.debug("  Cell[{}] contains SPACE: '{}'", i, cellValue);
					} else {
						cellsWithoutSpaces++;
						LOG.debug("  Cell[{}] no space: '{}'", i, cellValue);
					}
					
					// Check for common header keywords
					String lowerValue = cellValue.toLowerCase();
					if (lowerValue.contains("number") || lowerValue.contains("name") || 
						lowerValue.contains("id") || lowerValue.contains("date") || 
						lowerValue.contains("time") || lowerValue.contains("type") ||
						lowerValue.contains("code") || lowerValue.contains("description") ||
						lowerValue.contains("line") || lowerValue.contains("business") ||
						lowerValue.contains("administrator") || lowerValue.contains("company") ||
						lowerValue.contains("creation") || lowerValue.contains("layout")) {
						cellsWithSpaces++; // Treat keyword cells like header cells
						LOG.debug("  Cell[{}] contains HEADER keyword: '{}'", i, cellValue);
					}
				}
			}
		}

		LOG.debug("Row analysis: {} cells with spaces/keywords, {} cells without", 
			cellsWithSpaces, cellsWithoutSpaces);

		// If majority have spaces or header keywords, it's a header row
		if (cellsChecked > 0) {
			boolean isData = cellsWithoutSpaces > cellsWithSpaces;
			LOG.debug("Row conclusion: {}", isData ? "DATA" : "HEADERS");
			return isData;
		}

		return false;
	}

	/**
	 * Read headers from a specific sheet by sheet name.
	 * 
	 * @param sheetName name of the sheet to read
	 * @return array of header strings
	 * @throws IOException if file cannot be read or sheet not found
	 */
	public String[] readHeadersFromSheet(String sheetName) throws IOException {
		List<String> headers = new ArrayList<>();

		if (!layoutFile.exists()) {
			LOG.error("Layout header file not found: {}", layoutFile.getAbsolutePath());
			throw new IOException("Layout header file not found: " + layoutFile.getAbsolutePath());
		}

		try (FileInputStream fis = new FileInputStream(layoutFile);
				Workbook workbook = WorkbookFactory.create(fis)) {

			// Get the specified sheet
			Sheet sheet = workbook.getSheet(sheetName);
			if (sheet == null) {
				LOG.error("Sheet '{}' not found in layout header file", sheetName);
				throw new IOException("Sheet '" + sheetName + "' not found in layout header file");
			}

			// Get the first row
			Row headerRow = sheet.getRow(0);
			if (headerRow == null) {
				LOG.error("No header row found in sheet '{}'", sheetName);
				throw new IOException("No header row found in sheet '" + sheetName + "'");
			}

			// Extract all cells from the first row
			int lastCellNum = headerRow.getLastCellNum();
			LOG.info("Reading {} headers from sheet '{}' in layout file", lastCellNum, sheetName);

			for (int i = 0; i < lastCellNum; i++) {
				Cell cell = headerRow.getCell(i);
				String headerValue = "";

				if (cell != null) {
					headerValue = getCellValueAsString(cell);
					// IMPORTANT: Trim whitespace from headers
					headerValue = headerValue.trim();
				}

				headers.add(headerValue);
			}

			LOG.info("Successfully read {} headers from sheet '{}'", headers.size(), sheetName);
			return headers.toArray(new String[0]);

		} catch (IOException e) {
			LOG.error("Error reading layout header file: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Get cell value as string, handling different cell types.
	 * 
	 * @param cell the cell to read
	 * @return string representation of cell value
	 */
	private String getCellValueAsString(Cell cell) {
		switch (cell.getCellType()) {
		case STRING:
			return cell.getStringCellValue();
		case NUMERIC:
			return String.valueOf(cell.getNumericCellValue());
		case BOOLEAN:
			return String.valueOf(cell.getBooleanCellValue());
		case FORMULA:
			// Try to get cached formula result
			try {
				return cell.getStringCellValue();
			} catch (Exception e) {
				return "";
			}
		case BLANK:
			return "";
		default:
			return "";
		}
	}

	/**
	 * Get all sheet names in the workbook.
	 * 
	 * @return array of sheet names
	 * @throws IOException if file cannot be read
	 */
	public String[] getSheetNames() throws IOException {
		if (!layoutFile.exists()) {
			LOG.error("Layout header file not found: {}", layoutFile.getAbsolutePath());
			throw new IOException("Layout header file not found: " + layoutFile.getAbsolutePath());
		}

		try (FileInputStream fis = new FileInputStream(layoutFile);
				Workbook workbook = WorkbookFactory.create(fis)) {

			int sheetCount = workbook.getNumberOfSheets();
			String[] sheetNames = new String[sheetCount];

			for (int i = 0; i < sheetCount; i++) {
				sheetNames[i] = workbook.getSheetName(i);
			}

			LOG.info("Found {} sheets in layout file", sheetCount);
			return sheetNames;

		} catch (IOException e) {
			LOG.error("Error reading layout header file: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Validate that the layout file exists and is readable.
	 * 
	 * @return true if file exists and is readable, false otherwise
	 */
	public boolean validateFile() {
		boolean exists = layoutFile.exists();
		boolean readable = layoutFile.canRead();

		if (!exists) {
			LOG.error("Layout file does not exist: {}", layoutFile.getAbsolutePath());
		}
		if (!readable) {
			LOG.error("Layout file is not readable: {}", layoutFile.getAbsolutePath());
		}

		return exists && readable;
	}

	/**
	 * Get the file path.
	 * 
	 * @return layout file path
	 */
	public String getFilePath() {
		return layoutFile.getAbsolutePath();
	}
}
