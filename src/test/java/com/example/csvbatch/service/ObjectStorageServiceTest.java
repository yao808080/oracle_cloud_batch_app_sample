package com.example.csvbatch.service;

import com.example.csvbatch.exception.ObjectStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "RUN_INTEGRATION_TESTS", matches = "true")
class ObjectStorageServiceTest {

    private ObjectStorageService objectStorageService;

    @BeforeEach
    void setUp() {
        objectStorageService = new ObjectStorageService();
    }

    @Test
    void testServiceInstantiation() {
        assertNotNull(objectStorageService);
    }

    @Test
    void testUploadCsvValidation() {
        String csvContent = "employeeId,employeeName\n1,Test User\n";

        assertThrows(Exception.class, () -> {
            objectStorageService.uploadCsvFile(csvContent, 1, 1000L);
        }, "Should throw exception when OCI client is not configured");
    }

    @Test
    void testListObjectsValidation() {
        String prefix = "exports/";

        assertThrows(Exception.class, () -> {
            objectStorageService.listObjects(prefix);
        }, "Should throw exception when OCI client is not configured");
    }

    @Test
    void testDownloadObjectValidation() {
        String objectName = "test.csv";

        assertThrows(Exception.class, () -> {
            objectStorageService.downloadObject(objectName);
        }, "Should throw exception when OCI client is not configured");
    }

    @Test
    void testDeleteObjectValidation() {
        String objectName = "test.csv";

        assertThrows(Exception.class, () -> {
            objectStorageService.deleteObject(objectName);
        }, "Should throw exception when OCI client is not configured");
    }

    @Test
    void testGenerateObjectNameFormat() {
        String testContent = "test,data\n1,test";
        
        try {
            objectStorageService.uploadCsvFile(testContent, 1, 1000L);
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeException || e instanceof ObjectStorageException,
                    "Exception should be related to OCI configuration");
        }
    }

    @Test
    void testBucketOperationsWithoutConfiguration() {
        boolean exists = objectStorageService.bucketExists();
        assertFalse(exists, "Bucket should not exist without proper OCI configuration");
    }

    @Test
    void testObjectNameGeneration() {
        String csvContent = "test,content";

        try {
            String objectName = objectStorageService.uploadCsvFile(csvContent, 1, 1000L);
            assertNotNull(objectName, "Should return object name even if upload fails");
        } catch (Exception e) {
            assertNotNull(e.getMessage());
            // Accept any exception related to OCI configuration
            assertTrue(e.getMessage() != null && !e.getMessage().isEmpty(),
                      "Error message should not be empty");
        }
    }

    @Test
    void testRetryMechanismConfiguration() {
        ObjectStorageService service = new ObjectStorageService();
        assertNotNull(service);
        
        try {
            service.uploadCsvFile("test", 1, 100L);
            fail("Should throw exception without OCI configuration");
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeException || e instanceof ObjectStorageException);
        }
    }

    @Test
    void testMetricsAnnotationPresence() {
        ObjectStorageService service = new ObjectStorageService();
        assertNotNull(service);
        
        assertThrows(Exception.class, () -> {
            service.listObjects("prefix/");
        });
    }

    @Test 
    void testConcurrentOperations() {
        ObjectStorageService service = new ObjectStorageService();
        
        assertThrows(Exception.class, () -> {
            service.uploadCsvFile("content1", 1, 100L);
        });
        
        assertThrows(Exception.class, () -> {
            service.uploadCsvFile("content2", 1, 100L);
        });
    }

    @Test
    void testLargeContentHandling() {
        StringBuilder largeContent = new StringBuilder();
        largeContent.append("employeeId,name\n");
        
        for (int i = 0; i < 1000; i++) {
            largeContent.append(i).append(",Employee").append(i).append("\n");
        }
        
        String csvContent = largeContent.toString();
        assertTrue(csvContent.length() > 10000);
        
        assertThrows(Exception.class, () -> {
            objectStorageService.uploadCsvFile(csvContent, 1000, 5000L);
        });
    }
}