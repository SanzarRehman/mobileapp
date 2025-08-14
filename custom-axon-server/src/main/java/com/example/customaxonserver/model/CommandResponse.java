package com.example.customaxonserver.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents the response from command processing.
 */
public class CommandResponse {
    
    private final String commandId;
    private final String status;
    private final String targetInstance;
    private final Object result;
    private final String errorMessage;
    private final Instant timestamp;
    
    @JsonCreator
    public CommandResponse(
            @JsonProperty("commandId") String commandId,
            @JsonProperty("status") String status,
            @JsonProperty("targetInstance") String targetInstance,
            @JsonProperty("result") Object result,
            @JsonProperty("errorMessage") String errorMessage) {
        
        this.commandId = commandId;
        this.status = status;
        this.targetInstance = targetInstance;
        this.result = result;
        this.errorMessage = errorMessage;
        this.timestamp = Instant.now();
    }
    
    public static CommandResponse success(String commandId, String targetInstance, Object result) {
        return new CommandResponse(commandId, "SUCCESS", targetInstance, result, null);
    }
    
    public static CommandResponse routed(String commandId, String targetInstance) {
        return new CommandResponse(commandId, "ROUTED", targetInstance, null, null);
    }
    
    public static CommandResponse error(String commandId, String errorMessage) {
        return new CommandResponse(commandId, "ERROR", null, null, errorMessage);
    }
    
    public String getCommandId() {
        return commandId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public String getTargetInstance() {
        return targetInstance;
    }
    
    public Object getResult() {
        return result;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "CommandResponse{" +
                "commandId='" + commandId + '\'' +
                ", status='" + status + '\'' +
                ", targetInstance='" + targetInstance + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}