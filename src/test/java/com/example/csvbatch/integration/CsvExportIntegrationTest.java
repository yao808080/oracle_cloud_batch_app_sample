package com.example.csvbatch.integration;

import com.example.csvbatch.service.CsvExportService;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

@HelidonTest
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
class CsvExportIntegrationTest {
    
    @Inject
    private CsvExportService csvExportService;
    
    @Test
    void testFullCsvExportProcess() {
        try {
            String result = csvExportService.exportEmployeesToCsv();
            
            assertNotNull(result);
            assertFalse(result.trim().isEmpty());
            
        } catch (Exception e) {
            fail("CSV export integration test failed: " + e.getMessage());
        }
    }
}