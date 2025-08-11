package com.example.csvbatch.service;

import com.example.csvbatch.client.SoapClient;
import com.example.csvbatch.dto.EmployeeCsvData;
import com.example.csvbatch.dto.EmployeeDetails;
import com.example.csvbatch.entity.Employee;
import com.example.csvbatch.exception.CsvProcessingException;
import com.example.csvbatch.repository.EmployeeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.Timed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class CsvExportService {
    
    private static final Logger LOGGER = Logger.getLogger(CsvExportService.class.getName());
    
    @Inject
    private EmployeeRepository employeeRepository;
    
    @Inject
    private SoapClient soapClient;
    
    @Inject
    private CsvProcessorService csvProcessorService;
    
    @Inject
    private ObjectStorageService objectStorageService;
    
    @Inject
    @ConfigProperty(name = "csv.export.enabled", defaultValue = "true")
    private boolean exportEnabled;
    
    @Inject
    @ConfigProperty(name = "csv.export.storage-upload", defaultValue = "true")
    private boolean storageUploadEnabled;
    
    @Inject
    @ConfigProperty(name = "csv.batch.size", defaultValue = "1000")
    private int batchSize;
    
    @Inject
    @Metric(name = "csv.export.errors")
    private Counter errorCounter;
    
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    
    @Counted(name = "csv.export.total")
    @Timed(name = "csv.export.duration")
    @Retry(maxRetries = 2, delay = 5000)
    public String exportEmployeesToCsv() {
        if (!exportEnabled) {
            LOGGER.warning("CSV export is disabled");
            return null;
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            LOGGER.info("Starting CSV export process");
            
            List<Employee> employees = employeeRepository.findAll();
            if (employees.isEmpty()) {
                LOGGER.warning("No employees found in database");
                throw new CsvProcessingException("No employee data to export");
            }
            
            LOGGER.info("Retrieved " + employees.size() + " employees from database");
            
            List<EmployeeCsvData> csvDataList = processEmployees(employees);
            
            csvProcessorService.validateCsvData(csvDataList);
            
            String csvContent = csvProcessorService.writeCsvToString(csvDataList);
            
            csvProcessorService.saveToLocalFile(csvContent);
            
            String objectName = null;
            if (storageUploadEnabled) {
                long processingTime = System.currentTimeMillis() - startTime;
                objectName = objectStorageService.uploadCsvFile(csvContent, csvDataList.size(), processingTime);
                LOGGER.info("CSV uploaded to Object Storage: " + objectName);
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            LOGGER.info("CSV export completed successfully in " + totalTime + " ms");
            
            return objectName != null ? objectName : "local:result.csv";
            
        } catch (Exception e) {
            errorCounter.inc();
            LOGGER.log(Level.SEVERE, "CSV export failed", e);
            throw new CsvProcessingException("CSV export process failed", e);
        }
    }
    
    private List<EmployeeCsvData> processEmployees(List<Employee> employees) {
        LOGGER.info("Processing " + employees.size() + " employees with SOAP API calls");
        
        List<List<Employee>> batches = createBatches(employees, batchSize);
        List<EmployeeCsvData> allCsvData = new ArrayList<>();
        
        for (int i = 0; i < batches.size(); i++) {
            List<Employee> batch = batches.get(i);
            LOGGER.info("Processing batch " + (i + 1) + "/" + batches.size() + " with " + batch.size() + " records");
            
            List<CompletableFuture<EmployeeCsvData>> futures = batch.stream()
                    .map(employee -> CompletableFuture
                            .supplyAsync(() -> enrichEmployeeData(employee), executorService))
                    .collect(Collectors.toList());
            
            List<EmployeeCsvData> batchResults = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(data -> data != null)
                    .collect(Collectors.toList());
            
            allCsvData.addAll(batchResults);
        }
        
        LOGGER.info("Processed " + allCsvData.size() + " employees successfully");
        return allCsvData;
    }
    
    private EmployeeCsvData enrichEmployeeData(Employee employee) {
        try {
            EmployeeDetails details = soapClient.getEmployeeDetails(employee.getEmployeeId());
            
            return EmployeeCsvData.builder()
                    .employeeId(employee.getEmployeeId())
                    .employeeName(employee.getEmployeeName())
                    .department(employee.getDepartment())
                    .email(employee.getEmail())
                    .hireDate(employee.getHireDate())
                    .salary(employee.getSalary())
                    .level(details.getLevel())
                    .bonus(details.getBonus())
                    .status(details.getStatus())
                    .build();
                    
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to enrich data for employee: " + employee.getEmployeeId(), e);
            
            return EmployeeCsvData.builder()
                    .employeeId(employee.getEmployeeId())
                    .employeeName(employee.getEmployeeName())
                    .department(employee.getDepartment())
                    .email(employee.getEmail())
                    .hireDate(employee.getHireDate())
                    .salary(employee.getSalary())
                    .level("Error")
                    .bonus(null)
                    .status("Error")
                    .build();
        }
    }
    
    private List<List<Employee>> createBatches(List<Employee> employees, int batchSize) {
        List<List<Employee>> batches = new ArrayList<>();
        for (int i = 0; i < employees.size(); i += batchSize) {
            int end = Math.min(i + batchSize, employees.size());
            batches.add(employees.subList(i, end));
        }
        return batches;
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
}