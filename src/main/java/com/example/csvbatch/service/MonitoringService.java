package com.example.csvbatch.service;

import com.oracle.bmc.monitoring.MonitoringClient;
import com.oracle.bmc.monitoring.model.Datapoint;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Metric;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MonitoringService {
    
    private static final Logger LOGGER = Logger.getLogger(MonitoringService.class.getName());
    
    @Inject
    private MonitoringClient monitoringClient;
    
    @Inject
    @ConfigProperty(name = "oci.region")
    private String region;
    
    @Inject
    @Metric(name = "app.processing.records")
    private Counter recordsProcessedCounter;
    
    @Inject
    @Metric(name = "app.processing.errors")
    private Counter processingErrorsCounter;
    
    private final AtomicInteger currentActiveJobs = new AtomicInteger(0);
    private final Map<String, Long> performanceMetrics = new HashMap<>();
    
    public void recordProcessedRecords(int count) {
        for (int i = 0; i < count; i++) {
            recordsProcessedCounter.inc();
        }
        
        sendCustomMetric("csv_batch_processor", "records_processed", (double) count);
    }
    
    public void recordProcessingError() {
        processingErrorsCounter.inc();
        sendCustomMetric("csv_batch_processor", "processing_errors", 1.0);
    }
    
    public void recordJobStart() {
        currentActiveJobs.incrementAndGet();
        sendCustomMetric("csv_batch_processor", "active_jobs", currentActiveJobs.get());
    }
    
    public void recordJobCompletion(long durationMs) {
        currentActiveJobs.decrementAndGet();
        performanceMetrics.put("last_job_duration_ms", durationMs);
        
        sendCustomMetric("csv_batch_processor", "active_jobs", currentActiveJobs.get());
        sendCustomMetric("csv_batch_processor", "job_duration_ms", (double) durationMs);
    }
    
    public void recordDatabaseOperationTime(String operation, long durationMs) {
        performanceMetrics.put("db_" + operation + "_ms", durationMs);
        sendCustomMetric("csv_batch_processor", "db_operation_duration", (double) durationMs);
    }
    
    public void recordObjectStorageOperationTime(String operation, long durationMs) {
        performanceMetrics.put("storage_" + operation + "_ms", durationMs);
        sendCustomMetric("csv_batch_processor", "storage_operation_duration", (double) durationMs);
    }
    
    private void sendCustomMetric(String namespace, String metricName, double value) {
        try {
            Map<String, String> dimensions = new HashMap<>();
            dimensions.put("service", "csv-batch-processor");
            dimensions.put("region", region);
            dimensions.put("environment", System.getProperty("app.environment", "development"));
            
            Datapoint datapoint = Datapoint.builder()
                    .timestamp(Date.from(Instant.now()))
                    .value(value)
                    .count(1)
                    .build();
            
            MetricDataDetails metricData = MetricDataDetails.builder()
                    .namespace(namespace)
                    .name(metricName)
                    .dimensions(dimensions)
                    .datapoints(Arrays.asList(datapoint))
                    .build();
            
            PostMetricDataDetails postMetricDataDetails = PostMetricDataDetails.builder()
                    .metricData(Arrays.asList(metricData))
                    .build();
            
            PostMetricDataRequest request = PostMetricDataRequest.builder()
                    .postMetricDataDetails(postMetricDataDetails)
                    .build();
            
            monitoringClient.postMetricData(request);
            
            LOGGER.fine("Sent custom metric: " + metricName + " = " + value);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send custom metric: " + metricName, e);
        }
    }
    
    public Gauge<Integer> createActiveJobsGauge() {
        return currentActiveJobs::get;
    }
    
    public Gauge<Long> createLastJobDurationGauge() {
        return () -> performanceMetrics.getOrDefault("last_job_duration_ms", 0L);
    }
    
    public Map<String, Object> getApplicationMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("records_processed", recordsProcessedCounter.getCount());
        metrics.put("processing_errors", processingErrorsCounter.getCount());
        metrics.put("active_jobs", currentActiveJobs.get());
        metrics.putAll(performanceMetrics);
        return metrics;
    }
    
    public void sendHealthCheckMetric(String component, boolean isHealthy) {
        sendCustomMetric("csv_batch_processor", "health_check_" + component, isHealthy ? 1.0 : 0.0);
    }
}