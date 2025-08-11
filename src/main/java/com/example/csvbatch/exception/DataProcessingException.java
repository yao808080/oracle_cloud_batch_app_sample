package com.example.csvbatch.exception;

public class DataProcessingException extends RuntimeException {
    
    private final String errorCode;
    
    public DataProcessingException(String message) {
        super(message);
        this.errorCode = "DATA_PROCESSING_ERROR";
    }
    
    public DataProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "DATA_PROCESSING_ERROR";
    }
    
    public DataProcessingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public DataProcessingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}