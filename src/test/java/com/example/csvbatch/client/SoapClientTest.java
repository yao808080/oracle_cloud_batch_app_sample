package com.example.csvbatch.client;

import com.example.csvbatch.dto.EmployeeDetails;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@HelidonTest
@EnabledIfSystemProperty(named = "test.soap.enabled", matches = "true")
class SoapClientTest {
    
    @Inject
    private SoapClient soapClient;
    
    @Test
    void testGetEmployeeDetails_Success() {
        EmployeeDetails details = soapClient.getEmployeeDetails(1001L);
        
        assertNotNull(details);
        assertEquals(1001L, details.getEmployeeId());
        assertNotNull(details.getLevel());
        assertNotNull(details.getBonus());
        assertNotNull(details.getStatus());
    }
    
    @Test
    void testGetEmployeeDetails_NonExistingEmployee() {
        EmployeeDetails details = soapClient.getEmployeeDetails(-1L);
        
        assertNotNull(details);
        assertEquals(-1L, details.getEmployeeId());
    }
    
    @Test
    void testFallbackMethod() {
        EmployeeDetails fallback = soapClient.getEmployeeDetailsFallback(9999L);
        
        assertNotNull(fallback);
        assertEquals(9999L, fallback.getEmployeeId());
        assertEquals("Unknown", fallback.getLevel());
        assertEquals(BigDecimal.ZERO, fallback.getBonus());
        assertEquals("Unavailable", fallback.getStatus());
    }
}