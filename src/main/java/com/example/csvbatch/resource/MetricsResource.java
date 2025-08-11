package com.example.csvbatch.resource;

import com.example.csvbatch.config.DatabaseConfig;
import com.example.csvbatch.service.MonitoringService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Path("/api/metrics")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class MetricsResource {
    
    @Inject
    private MonitoringService monitoringService;
    
    @Inject
    private DatabaseConfig databaseConfig;
    
    @GET
    @Path("/application")
    public Response getApplicationMetrics() {
        Map<String, Object> metrics = monitoringService.getApplicationMetrics();
        metrics.put("timestamp", LocalDateTime.now().toString());
        
        return Response.ok(metrics).build();
    }
    
    @GET
    @Path("/database")
    public Response getDatabaseMetrics() {
        DatabaseConfig.PoolStatistics stats = databaseConfig.getPoolStatistics();
        
        Map<String, Object> dbMetrics = new HashMap<>();
        dbMetrics.put("active_connections", stats.getActiveConnections());
        dbMetrics.put("available_connections", stats.getAvailableConnections());
        dbMetrics.put("total_connections", stats.getTotalConnections());
        dbMetrics.put("timestamp", LocalDateTime.now().toString());
        
        return Response.ok(dbMetrics).build();
    }
    
    @GET
    @Path("/summary")
    public Response getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        Map<String, Object> appMetrics = monitoringService.getApplicationMetrics();
        DatabaseConfig.PoolStatistics dbStats = databaseConfig.getPoolStatistics();
        
        summary.put("application", appMetrics);
        summary.put("database", Map.of(
            "active_connections", dbStats.getActiveConnections(),
            "available_connections", dbStats.getAvailableConnections(),
            "total_connections", dbStats.getTotalConnections()
        ));
        summary.put("timestamp", LocalDateTime.now().toString());
        
        return Response.ok(summary).build();
    }
}