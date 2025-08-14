package com.example.customaxonserver.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a command message that can be routed through the custom Axon server.
 */
public class CommandMessage {
    
    @NotBlank(message = "Command ID cannot be blank")
    private final String commandId;
    
    @NotBlank(message = "Command type cannot be blank")
    private final String commandType;
    
    @NotBlank(message = "Aggregate ID cannot be blank")
    private final String aggregateId;
    
    @NotNull(message = "Payload cannot be null")
    private final Object payload;
    
    private final Map<String, Object> metadata;
    
    private final Instant timestamp;
    
    @JsonCreator
    public CommandMessage(
            @JsonProperty("commandId") String commandId,
            @JsonProperty("commandType") String commandType,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("payload") Object payload,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        
        this.commandId = commandId != null ? commandId : UUID.randomUUID().toString();
        this.commandType = commandType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.metadata = metadata;
        this.timestamp = Instant.now();
    }
    
    public String getCommandId() {
        return commandId;
    }
    
    public String getCommandType() {
        return commandType;
    }
    
    public String getAggregateId() {
        return aggregateId;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "CommandMessage{" +
                "commandId='" + commandId + '\'' +
                ", commandType='" + commandType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}