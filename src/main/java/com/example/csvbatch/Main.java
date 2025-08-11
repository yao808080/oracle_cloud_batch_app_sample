package com.example.csvbatch;

import io.helidon.microprofile.server.Server;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    
    public static void main(String[] args) {
        Server server = Server.create().start();
        
        LOGGER.info("CSV Batch Processor started successfully");
        LOGGER.info("Server running at http://localhost:" + server.port());
        LOGGER.info("Health checks available at http://localhost:" + server.port() + "/health");
        LOGGER.info("Metrics available at http://localhost:" + server.port() + "/metrics");
    }
}