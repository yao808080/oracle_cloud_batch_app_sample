package com.example.csvbatch.health;

import com.example.csvbatch.repository.EmployeeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {
    
    @Inject
    private EmployeeRepository employeeRepository;
    
    @Override
    public HealthCheckResponse call() {
        try {
            boolean isConnected = employeeRepository.testConnection();
            
            if (isConnected) {
                return HealthCheckResponse.named("database-connection")
                        .up()
                        .withData("status", "UP")
                        .withData("database", "Oracle")
                        .withData("connection", "healthy")
                        .build();
            } else {
                return HealthCheckResponse.named("database-connection")
                        .down()
                        .withData("status", "DOWN")
                        .withData("error", "Connection test failed")
                        .build();
            }
            
        } catch (Exception e) {
            return HealthCheckResponse.named("database-connection")
                    .down()
                    .withData("status", "DOWN")
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}