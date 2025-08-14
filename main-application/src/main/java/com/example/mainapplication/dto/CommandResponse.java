package com.example.mainapplication.dto;

/**
 * Response DTO for command operations.
 */
public class CommandResponse {

    private String id;
    private String message;
    private boolean success;
    private long timestamp;

    // Default constructor
    public CommandResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor with all fields
    public CommandResponse(String id, String message, boolean success) {
        this.id = id;
        this.message = message;
        this.success = success;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "CommandResponse{" +
                "id='" + id + '\'' +
                ", message='" + message + '\'' +
                ", success=" + success +
                ", timestamp=" + timestamp +
                '}';
    }
}