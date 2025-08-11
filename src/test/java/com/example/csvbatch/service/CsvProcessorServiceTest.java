package com.example.csvbatch.service;

import com.example.csvbatch.dto.EmployeeCsvData;
import com.example.csvbatch.exception.CsvProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvProcessorServiceTest {
    
    private CsvProcessorService csvProcessorService;
    
    private List<EmployeeCsvData> testEmployees;
    
    @BeforeEach
    void setUp() {
        csvProcessorService = new CsvProcessorService();
        testEmployees = new ArrayList<>();
        
        testEmployees.add(EmployeeCsvData.builder()
                .employeeId(1001L)
                .employeeName("田中 太郎")
                .department("開発部")
                .email("tanaka@example.com")
                .hireDate(LocalDate.of(2020, 4, 1))
                .salary(new BigDecimal("500000.00"))
                .level("Senior")
                .bonus(new BigDecimal("150000.00"))
                .status("Active")
                .build());
                
        testEmployees.add(EmployeeCsvData.builder()
                .employeeId(1002L)
                .employeeName("佐藤 花子")
                .department("営業部")
                .email("sato@example.com")
                .hireDate(LocalDate.of(2019, 1, 15))
                .salary(new BigDecimal("450000.00"))
                .level("Mid")
                .bonus(new BigDecimal("100000.00"))
                .status("Active")
                .build());
    }
    
    @Test
    void testWriteCsvToString_Success() {
        String csvContent = csvProcessorService.writeCsvToString(testEmployees);
        
        assertNotNull(csvContent);
        assertFalse(csvContent.trim().isEmpty());
        
        String[] lines = csvContent.split("\n");
        assertTrue(lines.length >= 2);
        
        String header = lines[0];
        assertTrue(header.contains("EMPLOYEEID"));
        assertTrue(header.contains("EMPLOYEENAME"));
        assertTrue(header.contains("DEPARTMENT"));
        assertTrue(header.contains("EMAIL"));
        
        String firstDataLine = lines[1];
        assertTrue(firstDataLine.contains("1001"));
        assertTrue(firstDataLine.contains("田中 太郎"));
        assertTrue(firstDataLine.contains("開発部"));
    }
    
    @Test
    void testWriteCsvToString_EmptyList() {
        List<EmployeeCsvData> emptyList = new ArrayList<>();
        
        String csvContent = csvProcessorService.writeCsvToString(emptyList);
        
        assertNotNull(csvContent);
        String[] lines = csvContent.split("\n");
        assertEquals(1, lines.length);
    }
    
    @Test
    void testValidateCsvData_Success() {
        assertDoesNotThrow(() -> csvProcessorService.validateCsvData(testEmployees));
    }
    
    @Test
    void testValidateCsvData_NullList() {
        CsvProcessingException exception = assertThrows(CsvProcessingException.class, 
            () -> csvProcessorService.validateCsvData(null));
        
        assertEquals("CSV_VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("No employee data to export"));
    }
    
    @Test
    void testValidateCsvData_EmptyList() {
        List<EmployeeCsvData> emptyList = new ArrayList<>();
        
        CsvProcessingException exception = assertThrows(CsvProcessingException.class, 
            () -> csvProcessorService.validateCsvData(emptyList));
        
        assertEquals("CSV_VALIDATION_ERROR", exception.getErrorCode());
    }
    
    @Test
    void testValidateCsvData_HighErrorRate() {
        List<EmployeeCsvData> invalidData = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            invalidData.add(EmployeeCsvData.builder()
                    .employeeId(null)
                    .employeeName(null)
                    .build());
        }
        
        CsvProcessingException exception = assertThrows(CsvProcessingException.class, 
            () -> csvProcessorService.validateCsvData(invalidData));
        
        assertEquals("CSV_VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Error rate too high"));
    }
    
    @Test
    void testGetCsvHeaders() {
        String[] headers = csvProcessorService.getCsvHeaders();
        
        assertNotNull(headers);
        assertEquals(9, headers.length);
        assertEquals("employeeId", headers[0]);
        assertEquals("employeeName", headers[1]);
        assertEquals("status", headers[8]);
    }
    
    @Test
    void testCalculateBatchSize() {
        assertEquals(500, csvProcessorService.calculateBatchSize(500));
        assertEquals(1000, csvProcessorService.calculateBatchSize(5000));
        assertEquals(2000, csvProcessorService.calculateBatchSize(20000));
    }
}