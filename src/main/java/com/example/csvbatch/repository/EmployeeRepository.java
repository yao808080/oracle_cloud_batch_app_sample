package com.example.csvbatch.repository;

import com.example.csvbatch.entity.Employee;
import com.example.csvbatch.exception.DataProcessingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EmployeeRepository {
    
    private static final Logger LOGGER = Logger.getLogger(EmployeeRepository.class.getName());
    
    private static final String SELECT_ALL_EMPLOYEES = 
        "SELECT employee_id, employee_name, department, email, hire_date, salary " +
        "FROM employees ORDER BY employee_id";
    
    private static final String SELECT_EMPLOYEE_BY_ID = 
        "SELECT employee_id, employee_name, department, email, hire_date, salary " +
        "FROM employees WHERE employee_id = ?";
    
    private static final String COUNT_EMPLOYEES = 
        "SELECT COUNT(*) FROM employees";
    
    @Inject
    private DataSource dataSource;
    
    @Retry(maxRetries = 3, delay = 1000)
    @Counted(name = "employee.repository.findAll.count")
    @Timed(name = "employee.repository.findAll.time")
    public List<Employee> findAll() {
        List<Employee> employees = new ArrayList<>();
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_EMPLOYEES);
             ResultSet resultSet = statement.executeQuery()) {
            
            while (resultSet.next()) {
                employees.add(mapResultSetToEmployee(resultSet));
            }
            
            LOGGER.info("Retrieved " + employees.size() + " employees from database");
            return employees;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve employees from database", e);
            throw new DataProcessingException("DB_CONNECTION_ERROR", "Failed to retrieve employee data", e);
        }
    }
    
    @Retry(maxRetries = 3, delay = 1000)
    public Employee findById(Long employeeId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_EMPLOYEE_BY_ID)) {
            
            statement.setLong(1, employeeId);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToEmployee(resultSet);
                }
            }
            
            LOGGER.warning("Employee not found with ID: " + employeeId);
            return null;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve employee with ID: " + employeeId, e);
            throw new DataProcessingException("DB_CONNECTION_ERROR", 
                "Failed to retrieve employee with ID: " + employeeId, e);
        }
    }
    
    @Retry(maxRetries = 3, delay = 1000)
    public List<Employee> findAllWithPagination(int offset, int limit) {
        List<Employee> employees = new ArrayList<>();
        String paginatedQuery = SELECT_ALL_EMPLOYEES + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(paginatedQuery)) {
            
            statement.setInt(1, offset);
            statement.setInt(2, limit);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    employees.add(mapResultSetToEmployee(resultSet));
                }
            }
            
            LOGGER.info("Retrieved " + employees.size() + " employees (offset: " + offset + ", limit: " + limit + ")");
            return employees;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve employees with pagination", e);
            throw new DataProcessingException("DB_CONNECTION_ERROR", "Failed to retrieve employee data", e);
        }
    }
    
    @Retry(maxRetries = 3, delay = 1000)
    public int countEmployees() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_EMPLOYEES);
             ResultSet resultSet = statement.executeQuery()) {
            
            if (resultSet.next()) {
                int count = resultSet.getInt(1);
                LOGGER.info("Total employee count: " + count);
                return count;
            }
            
            return 0;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to count employees", e);
            throw new DataProcessingException("DB_CONNECTION_ERROR", "Failed to count employees", e);
        }
    }
    
    public boolean testConnection() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM DUAL");
             ResultSet resultSet = statement.executeQuery()) {
            
            return resultSet.next();
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database connection test failed", e);
            return false;
        }
    }
    
    private Employee mapResultSetToEmployee(ResultSet resultSet) throws SQLException {
        Employee employee = new Employee();
        employee.setEmployeeId(resultSet.getLong("employee_id"));
        employee.setEmployeeName(resultSet.getString("employee_name"));
        employee.setDepartment(resultSet.getString("department"));
        employee.setEmail(resultSet.getString("email"));
        
        Date hireDate = resultSet.getDate("hire_date");
        if (hireDate != null) {
            employee.setHireDate(hireDate.toLocalDate());
        }
        
        employee.setSalary(resultSet.getBigDecimal("salary"));
        
        return employee;
    }
}