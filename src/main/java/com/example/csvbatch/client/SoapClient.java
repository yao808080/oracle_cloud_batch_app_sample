package com.example.csvbatch.client;

import com.example.csvbatch.dto.EmployeeDetails;
import com.example.csvbatch.exception.DataProcessingException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.metrics.annotation.Counted;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

@ApplicationScoped
public class SoapClient {
    
    private static final Logger LOGGER = Logger.getLogger(SoapClient.class.getName());
    
    @Inject
    @ConfigProperty(name = "soap.api.url", defaultValue = "http://localhost:8080/ws")
    private String soapApiUrl;
    
    @Inject
    @ConfigProperty(name = "soap.api.timeout.connection", defaultValue = "30000")
    private int connectionTimeout;
    
    @Inject
    @ConfigProperty(name = "soap.api.timeout.read", defaultValue = "60000")
    private int readTimeout;
    
    private HttpClient httpClient;
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    @PostConstruct
    public void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectionTimeout))
                .build();
        
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();
        
        circuitBreaker = CircuitBreaker.of("soapService", cbConfig);
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .build();
        
        retry = Retry.of("soapRetry", retryConfig);
        
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                    LOGGER.info("Circuit breaker state transition: " + event.getStateTransition()));
    }
    
    @Counted(name = "soap.client.calls")
    @Fallback(fallbackMethod = "getEmployeeDetailsFallback")
    public EmployeeDetails getEmployeeDetails(Long employeeId) {
        LOGGER.info("Fetching details for employee ID: " + employeeId);
        
        Supplier<EmployeeDetails> decoratedSupplier = CircuitBreaker
                .decorateSupplier(circuitBreaker, 
                    Retry.decorateSupplier(retry, () -> callSoapApi(employeeId)));
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get employee details for ID: " + employeeId, e);
            throw new DataProcessingException("SOAP_API_ERROR", 
                "Failed to retrieve employee details from SOAP API", e);
        }
    }
    
    private EmployeeDetails callSoapApi(Long employeeId) {
        try {
            String soapRequest = buildSoapRequest(employeeId);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(soapApiUrl))
                    .header("Content-Type", "text/xml;charset=UTF-8")
                    .header("SOAPAction", "getEmployeeDetails")
                    .timeout(Duration.ofMillis(readTimeout))
                    .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("SOAP API returned status: " + response.statusCode());
            }
            
            return parseSoapResponse(response.body());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "SOAP API call failed for employee: " + employeeId, e);
            throw new RuntimeException("SOAP API call failed", e);
        }
    }
    
    private String buildSoapRequest(Long employeeId) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            
            Element envelope = doc.createElementNS("http://schemas.xmlsoap.org/soap/envelope/", "soap:Envelope");
            doc.appendChild(envelope);
            
            Element header = doc.createElementNS("http://schemas.xmlsoap.org/soap/envelope/", "soap:Header");
            envelope.appendChild(header);
            
            Element body = doc.createElementNS("http://schemas.xmlsoap.org/soap/envelope/", "soap:Body");
            envelope.appendChild(body);
            
            Element request = doc.createElementNS("http://example.com/employees", "emp:getEmployeeDetailsRequest");
            body.appendChild(request);
            
            Element empId = doc.createElementNS("http://example.com/employees", "emp:employeeId");
            empId.setTextContent(employeeId.toString());
            request.appendChild(empId);
            
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            
            return writer.toString();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to build SOAP request", e);
        }
    }
    
    private EmployeeDetails parseSoapResponse(String soapResponse) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(soapResponse)));
            
            doc.getDocumentElement().normalize();
            
            Element employeeDetails = (Element) doc.getElementsByTagNameNS(
                "http://example.com/employees", "employeeDetails").item(0);
            
            if (employeeDetails == null) {
                throw new RuntimeException("No employee details found in SOAP response");
            }
            
            Long id = Long.parseLong(getElementValue(employeeDetails, "employeeId"));
            String level = getElementValue(employeeDetails, "level");
            BigDecimal bonus = new BigDecimal(getElementValue(employeeDetails, "bonus"));
            String status = getElementValue(employeeDetails, "status");
            
            return EmployeeDetails.builder()
                    .employeeId(id)
                    .level(level)
                    .bonus(bonus)
                    .status(status)
                    .build();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SOAP response", e);
        }
    }
    
    private String getElementValue(Element parent, String tagName) {
        Element element = (Element) parent.getElementsByTagNameNS(
            "http://example.com/employees", tagName).item(0);
        return element != null ? element.getTextContent() : "";
    }
    
    public EmployeeDetails getEmployeeDetailsFallback(Long employeeId) {
        LOGGER.warning("Using fallback for employee ID: " + employeeId);
        
        return EmployeeDetails.builder()
                .employeeId(employeeId)
                .level("Unknown")
                .bonus(BigDecimal.ZERO)
                .status("Unavailable")
                .build();
    }
}