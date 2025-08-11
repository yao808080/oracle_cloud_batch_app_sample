package com.example.csvbatch.service;

import com.example.csvbatch.exception.ObjectStorageException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.*;
import com.oracle.bmc.objectstorage.requests.*;
import com.oracle.bmc.objectstorage.responses.*;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class ObjectStorageService {
    
    private static final Logger LOGGER = Logger.getLogger(ObjectStorageService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
    @Inject
    private ObjectStorage objectStorageClient;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.namespace")
    private String namespace;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.bucket", defaultValue = "csv-export-bucket")
    private String bucketName;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.prefix", defaultValue = "exports/")
    private String objectPrefix;
    
    @PostConstruct
    public void initializeBucket() {
        try {
            if (!bucketExists()) {
                createBucket();
                LOGGER.info("Bucket '" + bucketName + "' created successfully");
            } else {
                LOGGER.info("Bucket '" + bucketName + "' already exists");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to initialize bucket: " + bucketName, e);
        }
    }
    
    public boolean bucketExists() {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .build();
            
            objectStorageClient.headBucket(request);
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    public void createBucket() {
        try {
            CreateBucketDetails bucketDetails = CreateBucketDetails.builder()
                    .name(bucketName)
                    .compartmentId(getCompartmentId())
                    .publicAccessType(CreateBucketDetails.PublicAccessType.NoPublicAccess)
                    .storageTier(CreateBucketDetails.StorageTier.Standard)
                    .objectEventsEnabled(false)
                    .versioning(CreateBucketDetails.Versioning.Disabled)
                    .autoTiering(Bucket.AutoTiering.Disabled)
                    .build();
            
            CreateBucketRequest request = CreateBucketRequest.builder()
                    .namespaceName(namespace)
                    .createBucketDetails(bucketDetails)
                    .build();
            
            CreateBucketResponse response = objectStorageClient.createBucket(request);
            LOGGER.info("Bucket created: " + response.getBucket().getName());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create bucket", e);
            throw new ObjectStorageException("Failed to create bucket: " + bucketName, e);
        }
    }
    
    @Retry(maxRetries = 3, delay = 2000)
    @Counted(name = "objectstorage.upload.count")
    @Timed(name = "objectstorage.upload.time")
    public String uploadCsvFile(String csvContent, int recordCount, long processingTimeMs) {
        LocalDateTime now = LocalDateTime.now();
        String dateFolder = now.format(DATE_FORMATTER);
        String timestamp = now.format(TIMESTAMP_FORMATTER);
        String objectName = objectPrefix + dateFolder + "/result-" + timestamp + ".csv";
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("record-count", String.valueOf(recordCount));
        metadata.put("processing-time-ms", String.valueOf(processingTimeMs));
        metadata.put("upload-timestamp", now.toString());
        
        try {
            byte[] contentBytes = csvContent.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            
            PutObjectRequest request = PutObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .putObjectBody(inputStream)
                    .contentLength((long) contentBytes.length)
                    .contentType("text/csv")
                    .opcMeta(metadata)
                    .build();
            
            PutObjectResponse response = objectStorageClient.putObject(request);
            
            LOGGER.info("CSV file uploaded successfully to Object Storage");
            LOGGER.info("Object: " + objectName);
            LOGGER.info("ETag: " + response.getETag());
            LOGGER.info("Records: " + recordCount);
            
            return objectName;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to upload CSV to Object Storage", e);
            throw new ObjectStorageException("Failed to upload CSV file", e);
        }
    }
    
    @Retry(maxRetries = 3, delay = 1000)
    public InputStream downloadObject(String objectName) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build();
            
            GetObjectResponse response = objectStorageClient.getObject(request);
            
            LOGGER.info("Downloaded object: " + objectName);
            return response.getInputStream();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to download object: " + objectName, e);
            throw new ObjectStorageException("Failed to download object", e);
        }
    }
    
    public String downloadCsvFile(String objectName) {
        try (InputStream inputStream = downloadObject(objectName)) {
            Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to read CSV file: " + objectName, e);
            throw new ObjectStorageException("Failed to read CSV file", e);
        }
    }
    
    public List<ObjectSummary> listObjects(String prefix) {
        try {
            ListObjectsRequest request = ListObjectsRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .prefix(prefix != null ? prefix : objectPrefix)
                    .limit(1000)
                    .build();
            
            ListObjectsResponse response = objectStorageClient.listObjects(request);
            
            List<ObjectSummary> objects = response.getListObjects().getObjects();
            LOGGER.info("Listed " + objects.size() + " objects with prefix: " + prefix);
            
            return objects;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list objects", e);
            throw new ObjectStorageException("Failed to list objects", e);
        }
    }
    
    public List<String> listCsvFiles() {
        return listObjects(objectPrefix).stream()
                .map(ObjectSummary::getName)
                .filter(name -> name.endsWith(".csv"))
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }
    
    public void deleteObject(String objectName) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build();
            
            objectStorageClient.deleteObject(request);
            LOGGER.info("Deleted object: " + objectName);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete object: " + objectName, e);
            throw new ObjectStorageException("Failed to delete object", e);
        }
    }
    
    public void deleteCsvFile(String objectName) {
        if (!objectName.endsWith(".csv")) {
            throw new IllegalArgumentException("Not a CSV file: " + objectName);
        }
        deleteObject(objectName);
    }
    
    private String getCompartmentId() {
        return System.getenv("OCI_COMPARTMENT_ID");
    }
    
    public String getObjectUrl(String objectName) {
        return String.format("https://objectstorage.%s.oraclecloud.com/n/%s/b/%s/o/%s",
                System.getProperty("oci.region", "us-ashburn-1"),
                namespace,
                bucketName,
                objectName);
    }
}