package com.example.csvbatch.config;

import com.oracle.bmc.auth.*;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.monitoring.MonitoringClient;
import com.oracle.bmc.secrets.SecretsClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class OciConfig {
    
    private static final Logger LOGGER = Logger.getLogger(OciConfig.class.getName());
    
    @Inject
    @ConfigProperty(name = "oci.auth.method", defaultValue = "instance_principal")
    private String authMethod;
    
    @Inject
    @ConfigProperty(name = "oci.region", defaultValue = "us-ashburn-1")
    private String region;
    
    @Inject
    @ConfigProperty(name = "oci.config.profile", defaultValue = "DEFAULT")
    private String configProfile;
    
    @Inject
    @ConfigProperty(name = "oci.objectstorage.endpoint", defaultValue = "")
    private Optional<String> objectStorageEndpoint;
    
    @Produces
    @ApplicationScoped
    public AuthenticationDetailsProvider authenticationDetailsProvider() {
        LOGGER.info("Configuring OCI authentication with method: " + authMethod);
        
        switch (authMethod.toLowerCase()) {
            case "instance_principal":
                return createInstancePrincipalProvider();
            case "resource_principal":
                return createResourcePrincipalProvider();
            case "config_file":
                return createConfigFileProvider();
            default:
                throw new IllegalArgumentException("Unsupported OCI auth method: " + authMethod);
        }
    }
    
    @Produces
    @ApplicationScoped
    public ObjectStorage objectStorageClient(AuthenticationDetailsProvider authProvider) {
        ObjectStorageClient.Builder builder = ObjectStorageClient.builder()
                .region(com.oracle.bmc.Region.fromRegionId(region));
        
        if (objectStorageEndpoint.isPresent() && !objectStorageEndpoint.get().isEmpty()) {
            builder.endpoint(objectStorageEndpoint.get());
            LOGGER.info("Using custom Object Storage endpoint: " + objectStorageEndpoint.get());
        }
        
        ObjectStorageClient client = builder.build(authProvider);
        LOGGER.info("Object Storage client configured for region: " + region);
        
        return client;
    }
    
    @Produces
    @ApplicationScoped
    public SecretsClient secretsClient(AuthenticationDetailsProvider authProvider) {
        SecretsClient client = SecretsClient.builder()
                .region(com.oracle.bmc.Region.fromRegionId(region))
                .build(authProvider);
        
        LOGGER.info("Secrets client configured for region: " + region);
        return client;
    }
    
    @Produces
    @ApplicationScoped
    public MonitoringClient monitoringClient(AuthenticationDetailsProvider authProvider) {
        MonitoringClient client = MonitoringClient.builder()
                .region(com.oracle.bmc.Region.fromRegionId(region))
                .build(authProvider);
        
        LOGGER.info("Monitoring client configured for region: " + region);
        return client;
    }
    
    private AuthenticationDetailsProvider createInstancePrincipalProvider() {
        try {
            InstancePrincipalsAuthenticationDetailsProvider provider = 
                InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            return (AuthenticationDetailsProvider) provider;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to configure Instance Principal authentication: " + e.getMessage());
            throw new RuntimeException("Instance Principal authentication failed", e);
        }
    }
    
    private AuthenticationDetailsProvider createResourcePrincipalProvider() {
        try {
            ResourcePrincipalAuthenticationDetailsProvider provider = 
                ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            return (AuthenticationDetailsProvider) provider;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to configure Resource Principal authentication: " + e.getMessage());
            throw new RuntimeException("Resource Principal authentication failed", e);
        }
    }
    
    private AuthenticationDetailsProvider createConfigFileProvider() {
        try {
            ConfigFileAuthenticationDetailsProvider provider = 
                new ConfigFileAuthenticationDetailsProvider(configProfile);
            
            LOGGER.info("Config file authentication configured with profile: " + configProfile);
            return provider;
            
        } catch (IOException e) {
            LOGGER.severe("Failed to configure config file authentication: " + e.getMessage());
            throw new RuntimeException("Config file authentication failed", e);
        }
    }
}