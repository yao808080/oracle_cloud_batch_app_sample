package com.example.csvbatch.repository;

import com.example.csvbatch.entity.Employee;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@HelidonTest
@EnabledIfSystemProperty(named = "test.database.enabled", matches = "true")
class EmployeeRepositoryTest {
    
    @Inject
    private EmployeeRepository employeeRepository;
    
    @Test
    void testFindAll() {
        List<Employee> employees = employeeRepository.findAll();
        
        assertNotNull(employees);
    }
    
    @Test
    void testFindById_ExistingEmployee() {
        List<Employee> allEmployees = employeeRepository.findAll();
        
        if (!allEmployees.isEmpty()) {
            Long existingId = allEmployees.get(0).getEmployeeId();
            Employee employee = employeeRepository.findById(existingId);
            
            assertNotNull(employee);
            assertEquals(existingId, employee.getEmployeeId());
        }
    }
    
    @Test
    void testFindById_NonExistingEmployee() {
        Employee employee = employeeRepository.findById(-1L);
        
        assertNull(employee);
    }
    
    @Test
    void testCountEmployees() {
        int count = employeeRepository.countEmployees();
        
        assertTrue(count >= 0);
    }
    
    @Test
    void testFindAllWithPagination() {
        List<Employee> employees = employeeRepository.findAllWithPagination(0, 5);
        
        assertNotNull(employees);
        assertTrue(employees.size() <= 5);
    }
    
    @Test
    void testConnection() {
        boolean isConnected = employeeRepository.testConnection();
        
        assertTrue(isConnected);
    }
}