package com.example.csvbatch.health;

import com.example.csvbatch.service.ObjectStorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class ObjectStorageHealthCheck implements HealthCheck {
    
    @Inject
    private ObjectStorageService objectStorageService;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.namespace")
    private String namespace;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.bucket")
    private String bucket;
    
    @Override
    public HealthCheckResponse call() {
        try {
            boolean bucketExists = objectStorageService.bucketExists();
            
            if (bucketExists) {
                return HealthCheckResponse.named("object-storage")
                        .up()
                        .withData("status", "UP")
                        .withData("namespace", namespace)
                        .withData("bucket", bucket)
                        .withData("accessible", true)
                        .build();
            } else {
                return HealthCheckResponse.named("object-storage")
                        .down()
                        .withData("status", "DOWN")
                        .withData("namespace", namespace)
                        .withData("bucket", bucket)
                        .withData("error", "Bucket not accessible")
                        .build();
            }
            
        } catch (Exception e) {
            return HealthCheckResponse.named("object-storage")
                    .down()
                    .withData("status", "DOWN")
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}