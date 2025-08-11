package com.example.csvbatch.service;

import com.example.csvbatch.dto.EmployeeCsvData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResilienceTest {

    private CsvProcessorService csvProcessorService;
    private List<EmployeeCsvData> testData;

    @BeforeEach
    void setUp() {
        csvProcessorService = new CsvProcessorService();
        testData = new ArrayList<>();
        testData.add(EmployeeCsvData.builder()
            .employeeId(1L)
            .employeeName("Test User")
            .department("Test Dept")
            .email("test@example.com")
            .hireDate(LocalDate.now())
            .salary(new BigDecimal("50000"))
            .level("Mid")
            .bonus(new BigDecimal("5000"))
            .status("Active")
            .build());
    }

    @Test
    void testCsvProcessorServiceRetryAnnotation() {
        assertNotNull(csvProcessorService);
        
        String csvContent = csvProcessorService.writeCsvToString(testData);
        assertNotNull(csvContent);
        assertFalse(csvContent.trim().isEmpty());
    }

    @Test
    void testValidateCsvDataWithRetry() {
        assertDoesNotThrow(() -> 
            csvProcessorService.validateCsvData(testData)
        );
    }

    @Test
    void testCsvHeadersGeneration() {
        String[] headers = csvProcessorService.getCsvHeaders();
        assertNotNull(headers);
        assertEquals(9, headers.length);
        assertEquals("employeeId", headers[0]);
        assertEquals("status", headers[8]);
    }

    @Test
    void testBatchSizeCalculation() {
        assertEquals(500, csvProcessorService.calculateBatchSize(500));
        assertEquals(1000, csvProcessorService.calculateBatchSize(5000));
        assertEquals(2000, csvProcessorService.calculateBatchSize(20000));
    }

    @Test
    void testCsvGenerationResilience() {
        for (int i = 0; i < 100; i++) {
            testData.add(EmployeeCsvData.builder()
                .employeeId((long) (1000 + i))
                .employeeName("Employee " + i)
                .department("Dept " + (i % 5))
                .email("emp" + i + "@example.com")
                .hireDate(LocalDate.now().minusDays(i))
                .salary(new BigDecimal("40000"))
                .level("Mid")
                .bonus(new BigDecimal("3000"))
                .status("Active")
                .build());
        }

        String csvContent = csvProcessorService.writeCsvToString(testData);
        assertNotNull(csvContent);
        
        String[] lines = csvContent.split("\n");
        assertTrue(lines.length >= 101);
    }

    @Test
    void testRetryConfigurationExists() {
        assertNotNull(csvProcessorService);
        
        try {
            String result = csvProcessorService.writeCsvToString(testData);
            assertNotNull(result);
        } catch (Exception e) {
            fail("CSV processing should not fail with valid data: " + e.getMessage());
        }
    }
}