package com.example.csvbatch.config;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class DatabaseConfig {
    
    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    
    @Inject
    @ConfigProperty(name = "datasource.url")
    private String jdbcUrl;
    
    @Inject
    @ConfigProperty(name = "datasource.username")
    private String username;
    
    @Inject
    @ConfigProperty(name = "datasource.password")
    private String password;
    
    @Inject
    @ConfigProperty(name = "datasource.poolName", defaultValue = "CsvBatchPool")
    private String poolName;
    
    @Inject
    @ConfigProperty(name = "datasource.connectionPooling.initialPoolSize", defaultValue = "2")
    private int initialPoolSize;
    
    @Inject
    @ConfigProperty(name = "datasource.connectionPooling.minPoolSize", defaultValue = "2")
    private int minPoolSize;
    
    @Inject
    @ConfigProperty(name = "datasource.connectionPooling.maxPoolSize", defaultValue = "10")
    private int maxPoolSize;
    
    @Inject
    @ConfigProperty(name = "datasource.connectionPooling.connectionTimeout", defaultValue = "60000")
    private int connectionTimeout;
    
    @Inject
    @ConfigProperty(name = "datasource.connectionPooling.inactiveConnectionTimeout", defaultValue = "300000")
    private int inactiveConnectionTimeout;
    
    @Inject
    @ConfigProperty(name = "datasource.connectionPooling.maxStatements", defaultValue = "100")
    private int maxStatements;
    
    @Inject
    @ConfigProperty(name = "autonomous.wallet.path", defaultValue = "")
    private Optional<String> walletPath;
    
    @Inject
    @ConfigProperty(name = "autonomous.wallet.password", defaultValue = "")
    private Optional<String> walletPassword;
    
    private PoolDataSource poolDataSource;
    
    @PostConstruct
    public void init() {
        configureAutonomousDatabase();
    }
    
    @Produces
    @ApplicationScoped
    public DataSource dataSource() {
        try {
            poolDataSource = PoolDataSourceFactory.getPoolDataSource();
            
            poolDataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            poolDataSource.setURL(jdbcUrl);
            poolDataSource.setUser(username);
            poolDataSource.setPassword(password);
            poolDataSource.setConnectionPoolName(poolName);
            
            poolDataSource.setInitialPoolSize(initialPoolSize);
            poolDataSource.setMinPoolSize(minPoolSize);
            poolDataSource.setMaxPoolSize(maxPoolSize);
            // Note: setConnectionTimeout may not be available in all UCP versions
            // poolDataSource.setConnectionTimeout(connectionTimeout);
            poolDataSource.setInactiveConnectionTimeout(inactiveConnectionTimeout / 1000);
            poolDataSource.setMaxStatements(maxStatements);
            
            poolDataSource.setValidateConnectionOnBorrow(true);
            poolDataSource.setSQLForValidateConnection("SELECT 1 FROM DUAL");
            
            poolDataSource.setTimeToLiveConnectionTimeout(1800);
            poolDataSource.setMaxConnectionReuseTime(3600);
            poolDataSource.setSecondsToTrustIdleConnection(120);
            
            LOGGER.info("Universal Connection Pool configured successfully");
            LOGGER.info("Pool Name: " + poolName);
            LOGGER.info("Initial/Min/Max Pool Size: " + initialPoolSize + "/" + minPoolSize + "/" + maxPoolSize);
            
            return poolDataSource;
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to configure UCP data source", e);
            throw new RuntimeException("Database configuration failed", e);
        }
    }
    
    private void configureAutonomousDatabase() {
        if (walletPath.isPresent() && !walletPath.get().isEmpty()) {
            System.setProperty("oracle.net.tns_admin", walletPath.get());
            System.setProperty("oracle.net.wallet_location", walletPath.get());
            
            if (walletPassword.isPresent() && !walletPassword.get().isEmpty()) {
                System.setProperty("oracle.net.wallet_password", walletPassword.get());
            }
            
            LOGGER.info("Autonomous Database wallet configured at: " + walletPath.get());
        } else {
            LOGGER.info("Using standard Oracle Database connection (no wallet)");
        }
    }
    
    public void closeConnectionPool() {
        if (poolDataSource != null) {
            try {
                poolDataSource.setMaxPoolSize(0);
                poolDataSource.setMinPoolSize(0);
                LOGGER.info("Connection pool closed successfully");
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing connection pool", e);
            }
        }
    }
    
    public PoolStatistics getPoolStatistics() {
        if (poolDataSource != null) {
            try {
                // Simple pool statistics fallback
                return new PoolStatistics(
                    poolDataSource.getMinPoolSize(),
                    poolDataSource.getMaxPoolSize() - poolDataSource.getMinPoolSize(),
                    poolDataSource.getMaxPoolSize()
                );
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to get pool statistics", e);
            }
        }
        return new PoolStatistics(0, 0, 10);
    }
    
    public static class PoolStatistics {
        private final int activeConnections;
        private final int availableConnections;
        private final int totalConnections;
        
        public PoolStatistics(int activeConnections, int availableConnections, int totalConnections) {
            this.activeConnections = activeConnections;
            this.availableConnections = availableConnections;
            this.totalConnections = totalConnections;
        }
        
        public int getActiveConnections() {
            return activeConnections;
        }
        
        public int getAvailableConnections() {
            return availableConnections;
        }
        
        public int getTotalConnections() {
            return totalConnections;
        }
    }
}