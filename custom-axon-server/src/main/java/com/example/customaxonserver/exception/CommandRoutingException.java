package com.example.customaxonserver.exception;

public class CommandRoutingException extends RuntimeException {
    
    public CommandRoutingException(String message) {
        super(message);
    }
    
    public CommandRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}