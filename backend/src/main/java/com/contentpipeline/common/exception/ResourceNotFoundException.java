package com.contentpipeline.common.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
    public ResourceNotFoundException(String type, Object id) {
        super(type + " not found: " + id);
    }
}
