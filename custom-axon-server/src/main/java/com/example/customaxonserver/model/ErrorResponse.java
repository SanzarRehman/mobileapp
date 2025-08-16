package com.example.customaxonserver.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String correlationId;
    private Map<String, String> details;

    public ErrorResponse() {}

    public ErrorResponse(LocalDateTime timestamp, int status, String error, String message, 
                        String path, String correlationId, Map<String, String> details) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.correlationId = correlationId;
        this.details = details;
    }

    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder();
    }

    // Getters and setters
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public Map<String, String> getDetails() { return details; }
    public void setDetails(Map<String, String> details) { this.details = details; }

    public static class ErrorResponseBuilder {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String path;
        private String correlationId;
        private Map<String, String> details;

        public ErrorResponseBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ErrorResponseBuilder status(int status) {
            this.status = status;
            return this;
        }

        public ErrorResponseBuilder error(String error) {
            this.error = error;
            return this;
        }

        public ErrorResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ErrorResponseBuilder path(String path) {
            this.path = path;
            return this;
        }

        public ErrorResponseBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public ErrorResponseBuilder details(Map<String, String> details) {
            this.details = details;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(timestamp, status, error, message, path, correlationId, details);
        }
    }
}