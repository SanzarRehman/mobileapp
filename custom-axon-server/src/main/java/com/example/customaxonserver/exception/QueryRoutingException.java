package com.example.customaxonserver.exception;

public class QueryRoutingException extends RuntimeException {
    
    public QueryRoutingException(String message) {
        super(message);
    }
    
    public QueryRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}