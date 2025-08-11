package com.example.csvbatch.exception;

public class CsvProcessingException extends RuntimeException {
    
    private final String errorCode;
    
    public CsvProcessingException(String message) {
        super(message);
        this.errorCode = "CSV_PROCESSING_ERROR";
    }
    
    public CsvProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "CSV_PROCESSING_ERROR";
    }
    
    public CsvProcessingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public CsvProcessingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}