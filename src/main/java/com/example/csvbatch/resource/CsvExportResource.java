package com.example.csvbatch.resource;

import com.example.csvbatch.service.CsvExportService;
import com.example.csvbatch.service.ObjectStorageService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Path("/api/csv")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CsvExportResource {
    
    private static final Logger LOGGER = Logger.getLogger(CsvExportResource.class.getName());
    
    @Inject
    private CsvExportService csvExportService;
    
    @Inject
    private ObjectStorageService objectStorageService;
    
    @POST
    @Path("/export")
    @Counted(name = "csv.export.api.calls")
    @Timed(name = "csv.export.api.duration")
    public Response exportCsv() {
        try {
            LOGGER.info("Received CSV export request via API");
            
            String objectName = csvExportService.exportEmployeesToCsv();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "CSV export completed successfully");
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("outputLocation", objectName);
            
            if (objectName != null && objectName.startsWith("exports/")) {
                response.put("downloadUrl", objectStorageService.getObjectUrl(objectName));
            }
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOGGER.severe("CSV export API failed: " + e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "CSV export failed: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse)
                    .build();
        }
    }
    
    @GET
    @Path("/status")
    public Response getExportStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "CSV Export Service");
        status.put("status", "running");
        status.put("timestamp", LocalDateTime.now().toString());
        
        return Response.ok(status).build();
    }
    
    @GET
    @Path("/files")
    public Response listCsvFiles() {
        try {
            List<String> csvFiles = objectStorageService.listCsvFiles();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("count", csvFiles.size());
            response.put("files", csvFiles);
            response.put("timestamp", LocalDateTime.now().toString());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOGGER.severe("Failed to list CSV files: " + e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to list CSV files: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse)
                    .build();
        }
    }
    
    @GET
    @Path("/download/{fileName}")
    @Produces("text/csv")
    public Response downloadCsvFile(@PathParam("fileName") String fileName) {
        try {
            if (!fileName.endsWith(".csv")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("File must be a CSV file")
                        .build();
            }
            
            String csvContent = objectStorageService.downloadCsvFile(fileName);
            
            return Response.ok(csvContent)
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Content-Type", "text/csv")
                    .build();
                    
        } catch (Exception e) {
            LOGGER.severe("Failed to download CSV file: " + e.getMessage());
            
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("File not found or download failed")
                    .build();
        }
    }
    
    @DELETE
    @Path("/files/{fileName}")
    public Response deleteCsvFile(@PathParam("fileName") String fileName) {
        try {
            objectStorageService.deleteCsvFile(fileName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "File deleted successfully: " + fileName);
            response.put("timestamp", LocalDateTime.now().toString());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOGGER.severe("Failed to delete CSV file: " + e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to delete file: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse)
                    .build();
        }
    }
}