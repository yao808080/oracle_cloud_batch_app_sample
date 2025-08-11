package com.example.csvbatch;

import com.example.csvbatch.resource.CsvExportResource;
import com.example.csvbatch.resource.MetricsResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HelidonFrameworkTest {

    @Test
    void testApplicationClassExists() {
        CsvBatchApplication app = new CsvBatchApplication();
        assertNotNull(app, "Application class should be instantiable");
    }

    @Test
    void testCsvExportResourceExists() {
        assertNotNull(CsvExportResource.class, "CsvExportResource class should exist");
    }

    @Test
    void testMetricsResourceExists() {
        assertNotNull(MetricsResource.class, "MetricsResource class should exist");
    }

    @Test
    void testMainClassExists() {
        assertNotNull(Main.class, "Main class should exist");
    }

    @Test
    void testResourceAnnotations() {
        assertTrue(CsvExportResource.class.isAnnotationPresent(jakarta.ws.rs.Path.class),
                  "CsvExportResource should have @Path annotation");
    }

    @Test
    void testApplicationScoped() {
        assertTrue(CsvBatchApplication.class.isAnnotationPresent(jakarta.enterprise.context.ApplicationScoped.class),
                  "CsvBatchApplication should have @ApplicationScoped annotation");
    }

    @Test
    void testPathAnnotation() {
        assertTrue(CsvBatchApplication.class.isAnnotationPresent(jakarta.ws.rs.ApplicationPath.class),
                  "CsvBatchApplication should have @ApplicationPath annotation");
    }

    @Test
    void testExportResourcePathConfiguration() {
        jakarta.ws.rs.Path pathAnnotation = CsvExportResource.class.getAnnotation(jakarta.ws.rs.Path.class);
        assertNotNull(pathAnnotation, "CsvExportResource should have @Path annotation");
        assertEquals("/api/csv", pathAnnotation.value(), "Path should be /api/csv");
    }

    @Test
    void testApplicationPathConfiguration() {
        jakarta.ws.rs.ApplicationPath appPathAnnotation = CsvBatchApplication.class.getAnnotation(jakarta.ws.rs.ApplicationPath.class);
        assertNotNull(appPathAnnotation, "Application should have @ApplicationPath annotation");
        assertEquals("/", appPathAnnotation.value(), "Application path should be /");
    }

    @Test
    void testJaxRsApplication() {
        assertTrue(jakarta.ws.rs.core.Application.class.isAssignableFrom(CsvBatchApplication.class),
                  "CsvBatchApplication should extend JAX-RS Application");
    }

    @Test
    void testResourceProducesJson() {
        jakarta.ws.rs.Produces producesAnnotation = CsvExportResource.class.getAnnotation(jakarta.ws.rs.Produces.class);
        assertNotNull(producesAnnotation, "CsvExportResource should have @Produces annotation");
        assertEquals("application/json", producesAnnotation.value()[0], "Should produce JSON");
    }

    @Test
    void testResourceConsumesJson() {
        jakarta.ws.rs.Consumes consumesAnnotation = CsvExportResource.class.getAnnotation(jakarta.ws.rs.Consumes.class);
        assertNotNull(consumesAnnotation, "CsvExportResource should have @Consumes annotation");
        assertEquals("application/json", consumesAnnotation.value()[0], "Should consume JSON");
    }

    @Test
    void testHelidonMpFrameworkAvailable() {
        try {
            Class<?> serverClass = Class.forName("io.helidon.microprofile.server.Server");
            assertNotNull(serverClass, "Helidon MP Server class should be available");
        } catch (ClassNotFoundException e) {
            fail("Helidon MP framework should be available in classpath");
        }
    }

    @Test
    void testMicroProfileConfigAvailable() {
        try {
            Class<?> configClass = Class.forName("org.eclipse.microprofile.config.Config");
            assertNotNull(configClass, "MicroProfile Config should be available");
        } catch (ClassNotFoundException e) {
            fail("MicroProfile Config should be available in classpath");
        }
    }

    @Test
    void testCdiAvailable() {
        try {
            Class<?> applicationScopedClass = Class.forName("jakarta.enterprise.context.ApplicationScoped");
            assertNotNull(applicationScopedClass, "CDI should be available");
        } catch (ClassNotFoundException e) {
            fail("CDI should be available in classpath");
        }
    }

    @Test
    void testJaxRsAvailable() {
        try {
            Class<?> jaxRsClass = Class.forName("jakarta.ws.rs.core.Application");
            assertNotNull(jaxRsClass, "JAX-RS should be available");
        } catch (ClassNotFoundException e) {
            fail("JAX-RS should be available in classpath");
        }
    }
}