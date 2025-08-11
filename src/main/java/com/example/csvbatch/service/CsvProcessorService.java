package com.example.csvbatch.service;

import com.example.csvbatch.dto.EmployeeCsvData;
import com.example.csvbatch.exception.CsvProcessingException;
import com.opencsv.CSVWriter;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class CsvProcessorService {
    
    private static final Logger LOGGER = Logger.getLogger(CsvProcessorService.class.getName());
    
    @Inject
    @ConfigProperty(name = "csv.output.path", defaultValue = "/app/output/result.csv")
    private String csvOutputPath;
    
    @Inject
    @ConfigProperty(name = "csv.export.local-backup", defaultValue = "true")
    private boolean localBackupEnabled;
    
    @Counted(name = "csv.export.count", description = "Total CSV exports")
    @Timed(name = "csv.export.duration", description = "CSV export duration")
    public String writeCsvToString(List<EmployeeCsvData> employees) {
        LOGGER.info("Writing CSV data for " + employees.size() + " employees");
        
        try (StringWriter writer = new StringWriter()) {
            StatefulBeanToCsv<EmployeeCsvData> beanToCsv = new StatefulBeanToCsvBuilder<EmployeeCsvData>(writer)
                    .withQuotechar(CSVWriter.DEFAULT_QUOTE_CHARACTER)
                    .withSeparator(CSVWriter.DEFAULT_SEPARATOR)
                    .withOrderedResults(true)
                    .build();
            
            beanToCsv.write(employees);
            String csvContent = writer.toString();
            
            LOGGER.info("CSV content generated successfully with " + employees.size() + " records");
            return csvContent;
            
        } catch (CsvDataTypeMismatchException | CsvRequiredFieldEmptyException e) {
            LOGGER.log(Level.SEVERE, "Error converting data to CSV", e);
            throw new CsvProcessingException("Failed to convert employee data to CSV", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IO error while writing CSV", e);
            throw new CsvProcessingException("IO error during CSV generation", e);
        }
    }
    
    @Retry(maxRetries = 3, delay = 1000)
    public void saveToLocalFile(String csvContent) {
        if (!localBackupEnabled) {
            LOGGER.info("Local backup is disabled, skipping file save");
            return;
        }
        
        try {
            Path outputPath = Paths.get(csvOutputPath);
            Path parentDir = outputPath.getParent();
            
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                LOGGER.info("Created output directory: " + parentDir);
            }
            
            Files.write(outputPath, csvContent.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            
            LOGGER.info("CSV file saved successfully to: " + outputPath);
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save CSV file locally", e);
            throw new CsvProcessingException("Failed to save CSV file to local filesystem", e);
        }
    }
    
    public void validateCsvData(List<EmployeeCsvData> employees) {
        if (employees == null || employees.isEmpty()) {
            throw new CsvProcessingException("CSV_VALIDATION_ERROR", "No employee data to export");
        }
        
        long invalidRecords = employees.stream()
                .filter(emp -> emp.getEmployeeId() == null || emp.getEmployeeName() == null)
                .count();
        
        if (invalidRecords > 0) {
            LOGGER.warning("Found " + invalidRecords + " invalid records in CSV data");
            
            double errorRate = (double) invalidRecords / employees.size();
            if (errorRate > 0.5) {
                throw new CsvProcessingException("CSV_VALIDATION_ERROR",
                        "Error rate too high: " + (errorRate * 100) + "% records are invalid");
            }
        }
        
        LOGGER.info("CSV data validation passed for " + employees.size() + " records");
    }
    
    public String[] getCsvHeaders() {
        return new String[] {
            "employeeId", "employeeName", "department", "email",
            "hireDate", "salary", "level", "bonus", "status"
        };
    }
    
    public int calculateBatchSize(int totalRecords) {
        if (totalRecords <= 1000) {
            return totalRecords;
        } else if (totalRecords <= 10000) {
            return 1000;
        } else {
            return 2000;
        }
    }
}