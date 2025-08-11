package com.example.csvbatch.exception;

public class ObjectStorageException extends RuntimeException {
    
    private final String errorCode;
    
    public ObjectStorageException(String message) {
        super(message);
        this.errorCode = "OBJECT_STORAGE_ERROR";
    }
    
    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "OBJECT_STORAGE_ERROR";
    }
    
    public ObjectStorageException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public ObjectStorageException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}