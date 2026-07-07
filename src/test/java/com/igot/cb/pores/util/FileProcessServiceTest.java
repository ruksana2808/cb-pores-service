package com.igot.cb.pores.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileProcessService Unit Tests")
class FileProcessServiceTest {

    @InjectMocks
    private FileProcessService fileProcessService;

    @Mock
    private MultipartFile multipartFile;

    private Workbook createMockWorkbook(String[][] data) {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        when(workbook.getSheetAt(0)).thenReturn(sheet);
        when(sheet.getLastRowNum()).thenReturn(data.length - 1);

        // Header row
        Row headerRow = mock(Row.class);
        when(sheet.getRow(0)).thenReturn(headerRow);
        int cols = data[0].length;
        when(headerRow.getLastCellNum()).thenReturn((short) cols);

        for (int j = 0; j < cols; j++) {
            Cell cell = mock(Cell.class);
            when(cell.getCellType()).thenReturn(CellType.STRING);
            RichTextString richText = mock(RichTextString.class);
            when(richText.getString()).thenReturn(data[0][j]);
            when(cell.getRichStringCellValue()).thenReturn(richText);
            when(headerRow.getCell(j)).thenReturn(cell);
        }

        // Data rows
        for (int i = 1; i < data.length; i++) {
            Row row = mock(Row.class);
            
            // Check if it's a completely blank row (representing exit loop)
            boolean isBlankRow = true;
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j] != null && !data[i][j].isEmpty()) {
                    isBlankRow = false;
                    break;
                }
            }
            if (isBlankRow) {
                when(sheet.getRow(i)).thenReturn(null);
                continue;
            }

            when(sheet.getRow(i)).thenReturn(row);
            for (int j = 0; j < data[i].length; j++) {
                Cell cell = mock(Cell.class);
                if (data[i][j] == null || data[i][j].isEmpty()) {
                    when(cell.getCellType()).thenReturn(CellType.BLANK);
                } else {
                    when(cell.getCellType()).thenReturn(CellType.STRING);
                    RichTextString richText = mock(RichTextString.class);
                    when(richText.getString()).thenReturn(data[i][j]);
                    when(cell.getRichStringCellValue()).thenReturn(richText);
                }
                when(row.getCell(j)).thenReturn(cell);
            }
        }
        return workbook;
    }

    private String createCsvContent(String[][] data) {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                csv.append(data[i][j]);
                if (j < data[i].length - 1) {
                    csv.append(",");
                }
            }
            csv.append("\n");
        }
        return csv.toString();
    }

    @BeforeEach
    void setUp() {
        // Setup is handled by Mockito annotations
    }

    @Test
    void testProcessExcelFileSuccess() throws IOException {
        // Given
        String[][] testData = {
                {"Name", "Age", "City"},
                {"John", "25", "New York"},
                {"Jane", "30", "Los Angeles"}
        };

        Workbook workbook = createMockWorkbook(testData);
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(multipartFile.getOriginalFilename()).thenReturn("test.xlsx");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        try (MockedStatic<WorkbookFactory> mockedFactory = mockStatic(WorkbookFactory.class)) {
            mockedFactory.when(() -> WorkbookFactory.create(any(InputStream.class)))
                    .thenReturn(workbook);

            // When
            List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

            // Then
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("John", result.get(0).get("Name"));
            assertEquals("25", result.get(0).get("Age"));
            assertEquals("New York", result.get(0).get("City"));
            assertEquals("Jane", result.get(1).get("Name"));
            assertEquals("30", result.get(1).get("Age"));
            assertEquals("Los Angeles", result.get(1).get("City"));
        }
    }

    @Test
    void testProcessExcelFileWithCsvFile() throws IOException {
        // Given
        String csvContent = createCsvContent(new String[][]{
                {"Name", "Age", "City"},
                {"John", "25", "New York"},
                {"Jane", "30", "Los Angeles"}
        });

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When
        List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("John", result.get(0).get("Name"));
        assertEquals("25", result.get(0).get("Age"));
        assertEquals("New York", result.get(0).get("City"));
    }

    @Test
    void testProcessExcelFileNullFilename() throws IOException {
        // Given
        when(multipartFile.getOriginalFilename()).thenReturn(null);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileProcessService.processExcelFile(multipartFile));
        assertEquals("File name is null", exception.getMessage());
    }

    @Test
    void testProcessExcelFileUnsupportedType() throws IOException {
        // Given
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);
        when(multipartFile.getOriginalFilename()).thenReturn("test.txt");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileProcessService.processExcelFile(multipartFile));
        assertTrue(exception.getMessage().contains("Unsupported file type"));
    }

    @Test
    void testProcessExcelFileIOException() throws IOException {
        // Given
        when(multipartFile.getOriginalFilename()).thenReturn("test.xlsx");
        when(multipartFile.getInputStream()).thenThrow(new IOException("Test IO Exception"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileProcessService.processExcelFile(multipartFile));
        assertEquals("Test IO Exception", exception.getMessage());
    }

    @Test
    @DisplayName("Test processExcelFile with general exception")
    void testProcessExcelFileGeneralException() throws IOException {
        // Given
        when(multipartFile.getOriginalFilename()).thenReturn("test.xlsx");
        when(multipartFile.getInputStream()).thenThrow(new RuntimeException("Test Exception"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileProcessService.processExcelFile(multipartFile));
        assertEquals("Test Exception", exception.getMessage());
    }

    @Test
    void testExcelProcessingWithBlankRows() throws IOException {
        // Given
        String[][] testData = {
                {"Name", "Age"},
                {"John", "25"},
                {"", ""},  // Blank row
                {"Jane", "30"}
        };

        Workbook workbook = createMockWorkbook(testData);
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(multipartFile.getOriginalFilename()).thenReturn("test.xlsx");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        try (MockedStatic<WorkbookFactory> mockedFactory = mockStatic(WorkbookFactory.class)) {
            mockedFactory.when(() -> WorkbookFactory.create(any(InputStream.class)))
                    .thenReturn(workbook);

            // When
            List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size()); // Stops at the first blank row
            assertEquals("John", result.get(0).get("Name"));
            assertEquals("25", result.get(0).get("Age"));
        }
    }

    @Test
    void testExcelProcessingWithDateCells() throws IOException {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        when(workbook.getSheetAt(0)).thenReturn(sheet);
        when(sheet.getLastRowNum()).thenReturn(1);

        Row headerRow = mock(Row.class);
        when(sheet.getRow(0)).thenReturn(headerRow);
        when(headerRow.getLastCellNum()).thenReturn((short) 2);

        Cell headerCell0 = mock(Cell.class);
        when(headerCell0.getCellType()).thenReturn(CellType.STRING);
        RichTextString rt0 = mock(RichTextString.class);
        when(rt0.getString()).thenReturn("Name");
        when(headerCell0.getRichStringCellValue()).thenReturn(rt0);
        when(headerRow.getCell(0)).thenReturn(headerCell0);

        Cell headerCell1 = mock(Cell.class);
        when(headerCell1.getCellType()).thenReturn(CellType.STRING);
        RichTextString rt1 = mock(RichTextString.class);
        when(rt1.getString()).thenReturn("Date");
        when(headerCell1.getRichStringCellValue()).thenReturn(rt1);
        when(headerRow.getCell(1)).thenReturn(headerCell1);

        Row dataRow = mock(Row.class);
        when(sheet.getRow(1)).thenReturn(dataRow);

        Cell dataCell0 = mock(Cell.class);
        when(dataCell0.getCellType()).thenReturn(CellType.STRING);
        RichTextString rtData0 = mock(RichTextString.class);
        when(rtData0.getString()).thenReturn("John");
        when(dataCell0.getRichStringCellValue()).thenReturn(rtData0);
        when(dataRow.getCell(0)).thenReturn(dataCell0);

        Cell dateCell = mock(Cell.class);
        when(dateCell.getCellType()).thenReturn(CellType.NUMERIC);
        Date dateVal = new Date();
        when(dateCell.getDateCellValue()).thenReturn(dateVal);
        when(dataRow.getCell(1)).thenReturn(dateCell);

        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(multipartFile.getOriginalFilename()).thenReturn("test.xlsx");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        try (MockedStatic<WorkbookFactory> mockedFactory = mockStatic(WorkbookFactory.class);
             MockedStatic<DateUtil> mockedDateUtil = mockStatic(DateUtil.class)) {
            mockedFactory.when(() -> WorkbookFactory.create(any(InputStream.class)))
                    .thenReturn(workbook);
            mockedDateUtil.when(() -> DateUtil.isCellDateFormatted(any(Cell.class)))
                    .thenReturn(true);

            // When
            List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("John", result.get(0).get("Name"));
            assertTrue(result.get(0).get("Date").contains("T")); // Should contain ISO format
        }
    }

    @Test
    void testExcelProcessingWithNullDataRow() throws IOException {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        when(workbook.getSheetAt(0)).thenReturn(sheet);
        when(sheet.getLastRowNum()).thenReturn(2);

        Row headerRow = mock(Row.class);
        when(sheet.getRow(0)).thenReturn(headerRow);
        when(headerRow.getLastCellNum()).thenReturn((short) 1);

        Cell headerCell = mock(Cell.class);
        when(headerCell.getCellType()).thenReturn(CellType.STRING);
        RichTextString rt = mock(RichTextString.class);
        when(rt.getString()).thenReturn("Name");
        when(headerCell.getRichStringCellValue()).thenReturn(rt);
        when(headerRow.getCell(0)).thenReturn(headerCell);

        // Row 1 is null
        when(sheet.getRow(1)).thenReturn(null);

        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(multipartFile.getOriginalFilename()).thenReturn("test.xlsx");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        try (MockedStatic<WorkbookFactory> mockedFactory = mockStatic(WorkbookFactory.class)) {
            mockedFactory.when(() -> WorkbookFactory.create(any(InputStream.class)))
                    .thenReturn(workbook);

            // When
            List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

            // Then
            assertNotNull(result);
            assertEquals(0, result.size()); // Should stop at null row
        }
    }

    @Test
    void testExcelProcessingWithBlankHeaderCells() throws IOException {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        when(workbook.getSheetAt(0)).thenReturn(sheet);
        when(sheet.getLastRowNum()).thenReturn(1);

        Row headerRow = mock(Row.class);
        when(sheet.getRow(0)).thenReturn(headerRow);
        when(headerRow.getLastCellNum()).thenReturn((short) 3);

        Cell headerCell0 = mock(Cell.class);
        when(headerCell0.getCellType()).thenReturn(CellType.STRING);
        RichTextString rt0 = mock(RichTextString.class);
        when(rt0.getString()).thenReturn("Name");
        when(headerCell0.getRichStringCellValue()).thenReturn(rt0);
        when(headerRow.getCell(0)).thenReturn(headerCell0);

        Cell blankHeaderCell = mock(Cell.class);
        when(blankHeaderCell.getCellType()).thenReturn(CellType.BLANK);
        when(headerRow.getCell(1)).thenReturn(blankHeaderCell);

        Cell headerCell2 = mock(Cell.class);
        when(headerCell2.getCellType()).thenReturn(CellType.STRING);
        RichTextString rt2 = mock(RichTextString.class);
        when(rt2.getString()).thenReturn("Age");
        when(headerCell2.getRichStringCellValue()).thenReturn(rt2);
        when(headerRow.getCell(2)).thenReturn(headerCell2);

        Row dataRow = mock(Row.class);
        when(sheet.getRow(1)).thenReturn(dataRow);

        Cell dataCell0 = mock(Cell.class);
        when(dataCell0.getCellType()).thenReturn(CellType.STRING);
        RichTextString rtD0 = mock(RichTextString.class);
        when(rtD0.getString()).thenReturn("John");
        when(dataCell0.getRichStringCellValue()).thenReturn(rtD0);
        when(dataRow.getCell(0)).thenReturn(dataCell0);

        Cell dataCell1 = mock(Cell.class);
        when(dataCell1.getCellType()).thenReturn(CellType.STRING);
        RichTextString rtD1 = mock(RichTextString.class);
        when(rtD1.getString()).thenReturn("Ignored");
        when(dataCell1.getRichStringCellValue()).thenReturn(rtD1);
        when(dataRow.getCell(1)).thenReturn(dataCell1);

        Cell dataCell2 = mock(Cell.class);
        when(dataCell2.getCellType()).thenReturn(CellType.STRING);
        RichTextString rtD2 = mock(RichTextString.class);
        when(rtD2.getString()).thenReturn("25");
        when(dataCell2.getRichStringCellValue()).thenReturn(rtD2);
        when(dataRow.getCell(2)).thenReturn(dataCell2);

        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(multipartFile.getOriginalFilename()).thenReturn("test.xlsx");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        try (MockedStatic<WorkbookFactory> mockedFactory = mockStatic(WorkbookFactory.class)) {
            mockedFactory.when(() -> WorkbookFactory.create(any(InputStream.class)))
                    .thenReturn(workbook);

            // When
            List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("John", result.get(0).get("Name"));
            assertEquals("25", result.get(0).get("Age"));
            assertFalse(result.get(0).containsKey("")); // Blank header should be ignored
        }
    }

    @Test
    void testExcelProcessingWithNewlines() throws IOException {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        when(workbook.getSheetAt(0)).thenReturn(sheet);
        when(sheet.getLastRowNum()).thenReturn(1);

        Row headerRow = mock(Row.class);
        when(sheet.getRow(0)).thenReturn(headerRow);
        when(headerRow.getLastCellNum()).thenReturn((short) 2);

        Cell headerCell0 = mock(Cell.class);
        when(headerCell0.getCellType()).thenReturn(CellType.STRING);
        RichTextString rt0 = mock(RichTextString.class);
        when(rt0.getString()).thenReturn("Name\n*");
        when(headerCell0.getRichStringCellValue()).thenReturn(rt0);
        when(headerRow.getCell(0)).thenReturn(headerCell0);

        Cell headerCell1 = mock(Cell.class);
        when(headerCell1.getCellType()).thenReturn(CellType.STRING);
        RichTextString rt1 = mock(RichTextString.class);
        when(rt1.getString()).thenReturn("Description");
        when(headerCell1.getRichStringCellValue()).thenReturn(rt1);
        when(headerRow.getCell(1)).thenReturn(headerCell1);

        Row dataRow = mock(Row.class);
        when(sheet.getRow(1)).thenReturn(dataRow);

        Cell dataCell0 = mock(Cell.class);
        when(dataCell0.getCellType()).thenReturn(CellType.STRING);
        RichTextString rtD0 = mock(RichTextString.class);
        when(rtD0.getString()).thenReturn("John");
        when(dataCell0.getRichStringCellValue()).thenReturn(rtD0);
        when(dataRow.getCell(0)).thenReturn(dataCell0);

        Cell dataCell1 = mock(Cell.class);
        when(dataCell1.getCellType()).thenReturn(CellType.STRING);
        RichTextString rtD1 = mock(RichTextString.class);
        when(rtD1.getString()).thenReturn("Line1\nLine2");
        when(dataCell1.getRichStringCellValue()).thenReturn(rtD1);
        when(dataRow.getCell(1)).thenReturn(dataCell1);

        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(multipartFile.getOriginalFilename()).thenReturn("test.xlsx");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        try (MockedStatic<WorkbookFactory> mockedFactory = mockStatic(WorkbookFactory.class)) {
            mockedFactory.when(() -> WorkbookFactory.create(any(InputStream.class)))
                    .thenReturn(workbook);

            // When
            List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("John", result.get(0).get("Name"));
            assertEquals("Line1,Line2", result.get(0).get("Description")); // Newline replaced with comma
        }
    }

    @Test
    void testCsvProcessingWithDateFormat() throws IOException {
        // Given
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        String dateString = dateFormat.format(new Date());

        String csvContent = "Name,Date\nJohn," + dateString + "\n";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When
        List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("John", result.get(0).get("Name"));
        assertTrue(result.get(0).get("Date").contains("T")); // Should contain ISO format
    }

    @Test
    void testCsvProcessingWithBlankCells() throws IOException {
        // Given
        String csvContent = "Name,Age\nJohn,25\n,\nJane,30\n";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When
        List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size()); // Should stop at blank row
        assertEquals("John", result.get(0).get("Name"));
        assertEquals("25", result.get(0).get("Age"));
    }

    @Test
    void testCsvProcessingWithNewlines() throws IOException {
        // Given
        String csvContent = "Name,Description\nJohn,\"Line1\nLine2\"\n";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When
        List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("John", result.get(0).get("Name"));
        assertEquals("Line1,Line2", result.get(0).get("Description")); // Newline replaced with comma
    }

    @Test
    void testCsvProcessingWithException() throws IOException {
        // Given
        String csvContent = "Name,Age\nJohn"; // Malformed CSV
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileProcessService.processExcelFile(multipartFile));
        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("Test Excel processing with exception in sheet processing")
    void testExcelProcessingWithException() throws IOException {
        // Given
        when(multipartFile.getOriginalFilename()).thenReturn("test.xlsx");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));

        try (MockedStatic<WorkbookFactory> mockedFactory = mockStatic(WorkbookFactory.class)) {
            mockedFactory.when(() -> WorkbookFactory.create(any(InputStream.class)))
                    .thenThrow(new RuntimeException("Sheet processing error"));

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> fileProcessService.processExcelFile(multipartFile));
            assertEquals("Sheet processing error", exception.getMessage());
        }
    }

    @Test
    void testXlsFileExtension() throws IOException {
        // Given
        String[][] testData = {
                {"Name", "Age"},
                {"John", "25"}
        };

        Workbook workbook = createMockWorkbook(testData);
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(multipartFile.getOriginalFilename()).thenReturn("test.xls");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        try (MockedStatic<WorkbookFactory> mockedFactory = mockStatic(WorkbookFactory.class)) {
            mockedFactory.when(() -> WorkbookFactory.create(any(InputStream.class)))
                    .thenReturn(workbook);

            // When
            List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("John", result.get(0).get("Name"));
            assertEquals("25", result.get(0).get("Age"));
        }
    }

    @Test
    void testCsvProcessingWithNullValues() throws IOException {
        // Given
        String csvContent = "Name,Age\nJohn,\n";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When
        List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("John", result.get(0).get("Name"));
        assertEquals("", result.get(0).get("Age")); // Null should be converted to empty string
    }

    @Test
    void testCsvProcessingWithWhitespaceValues() throws IOException {
        // Given
        String csvContent = "Name,Age\nJohn,   \n";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When
        List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

        // Then
        assertNotNull(result);
    }

    @Test
    void testDateParsingSuccess() throws IOException {
        // Given
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        String validDateString = dateFormat.format(new Date());

        String csvContent = "Name,Date\nJohn," + validDateString + "\n";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When
        List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).get("Date").contains("T"));
    }

    @Test
    void testDateParsingFailure() throws IOException {
        // Given
        String invalidDateString = "not-a-date";

        String csvContent = "Name,Date\nJohn," + invalidDateString + "\n";
        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes());

        when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
        when(multipartFile.getInputStream()).thenReturn(inputStream);

        // When
        List<Map<String, String>> result = fileProcessService.processExcelFile(multipartFile);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("not-a-date", result.get(0).get("Date")); // Should remain as is
    }
}