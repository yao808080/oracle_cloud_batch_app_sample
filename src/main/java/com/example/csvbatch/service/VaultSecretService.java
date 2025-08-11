package com.example.csvbatch.service;

import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.SecretBundle;
import com.oracle.bmc.secrets.model.SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class VaultSecretService {
    
    private static final Logger LOGGER = Logger.getLogger(VaultSecretService.class.getName());
    
    @Inject
    private SecretsClient secretsClient;
    
    @Inject
    @ConfigProperty(name = "oci.vault.secret.database-password.id", defaultValue = "")
    private Optional<String> databasePasswordSecretId;
    
    @Inject
    @ConfigProperty(name = "oci.vault.secret.soap-api-key.id", defaultValue = "")
    private Optional<String> soapApiKeySecretId;
    
    @Inject
    @ConfigProperty(name = "oci.vault.refresh.interval", defaultValue = "3600000")
    private long refreshInterval;
    
    private String cachedDatabasePassword;
    private String cachedSoapApiKey;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    @PostConstruct
    public void initializeSecrets() {
        LOGGER.info("Initializing OCI Vault secrets...");
        
        try {
            if (databasePasswordSecretId.isPresent() && !databasePasswordSecretId.get().isEmpty()) {
                cachedDatabasePassword = retrieveSecret(databasePasswordSecretId.get());
                LOGGER.info("Database password retrieved from OCI Vault");
            } else {
                LOGGER.info("Database password secret ID not configured, using environment variable");
            }
            
            if (soapApiKeySecretId.isPresent() && !soapApiKeySecretId.get().isEmpty()) {
                cachedSoapApiKey = retrieveSecret(soapApiKeySecretId.get());
                LOGGER.info("SOAP API key retrieved from OCI Vault");
            } else {
                LOGGER.info("SOAP API key secret ID not configured");
            }
            
            if (refreshInterval > 0) {
                scheduleSecretRefresh();
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to initialize secrets from OCI Vault. " +
                    "Falling back to environment variables", e);
        }
    }
    
    @Retry(maxRetries = 3, delay = 1000)
    private String retrieveSecret(String secretId) {
        try {
            GetSecretBundleRequest request = GetSecretBundleRequest.builder()
                    .secretId(secretId)
                    .build();
            
            GetSecretBundleResponse response = secretsClient.getSecretBundle(request);
            
            if (response.getSecretBundle() != null && 
                response.getSecretBundle().getSecretBundleContent() != null) {
                
                SecretBundleContentDetails content = response.getSecretBundle().getSecretBundleContent();
                
                if (content instanceof com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails) {
                    com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails base64Content = 
                        (com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails) content;
                    
                    String encodedContent = base64Content.getContent();
                    if (encodedContent != null) {
                        return new String(Base64.getDecoder().decode(encodedContent));
                    }
                }
            }
            
            throw new RuntimeException("Secret content is null for secret ID: " + secretId);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve secret from OCI Vault: " + secretId, e);
            throw new RuntimeException("Secret retrieval failed", e);
        }
    }
    
    public String getDatabasePassword() {
        if (cachedDatabasePassword != null && !cachedDatabasePassword.isEmpty()) {
            return cachedDatabasePassword;
        }
        
        String envPassword = System.getenv("DB_PASSWORD");
        if (envPassword != null && !envPassword.isEmpty()) {
            return envPassword;
        }
        
        String propertyPassword = System.getProperty("datasource.password");
        if (propertyPassword != null && !propertyPassword.isEmpty()) {
            return propertyPassword;
        }
        
        LOGGER.warning("No database password found in Vault or environment");
        return "";
    }
    
    public String getSoapApiKey() {
        if (cachedSoapApiKey != null && !cachedSoapApiKey.isEmpty()) {
            return cachedSoapApiKey;
        }
        
        String envKey = System.getenv("SOAP_API_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            return envKey;
        }
        
        return "";
    }
    
    private void scheduleSecretRefresh() {
        scheduler.scheduleAtFixedRate(this::refreshSecrets, 
            refreshInterval, refreshInterval, TimeUnit.MILLISECONDS);
        
        LOGGER.info("Scheduled secret refresh every " + (refreshInterval / 1000 / 60) + " minutes");
    }
    
    public void refreshSecrets() {
        LOGGER.info("Refreshing secrets from OCI Vault...");
        
        try {
            if (databasePasswordSecretId.isPresent() && !databasePasswordSecretId.get().isEmpty()) {
                String newPassword = retrieveSecret(databasePasswordSecretId.get());
                if (newPassword != null && !newPassword.equals(cachedDatabasePassword)) {
                    cachedDatabasePassword = newPassword;
                    LOGGER.info("Database password refreshed successfully");
                }
            }
            
            if (soapApiKeySecretId.isPresent() && !soapApiKeySecretId.get().isEmpty()) {
                String newKey = retrieveSecret(soapApiKeySecretId.get());
                if (newKey != null && !newKey.equals(cachedSoapApiKey)) {
                    cachedSoapApiKey = newKey;
                    LOGGER.info("SOAP API key refreshed successfully");
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to refresh secrets from OCI Vault", e);
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}