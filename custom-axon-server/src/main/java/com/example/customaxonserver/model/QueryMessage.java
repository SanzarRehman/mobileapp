package com.example.customaxonserver.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a query message that can be routed through the custom Axon server.
 */
public class QueryMessage {
    
    @NotBlank(message = "Query ID cannot be blank")
    private final String queryId;
    
    @NotBlank(message = "Query type cannot be blank")
    private final String queryType;
    
    @NotNull(message = "Payload cannot be null")
    private final Object payload;
    
    private final Map<String, Object> metadata;
    
    private final Instant timestamp;
    
    private final String responseType;
    
    @JsonCreator
    public QueryMessage(
            @JsonProperty("queryId") String queryId,
            @JsonProperty("queryType") String queryType,
            @JsonProperty("payload") Object payload,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("responseType") String responseType) {
        
        this.queryId = queryId != null ? queryId : UUID.randomUUID().toString();
        this.queryType = queryType;
        this.payload = payload;
        this.metadata = metadata;
        this.responseType = responseType;
        this.timestamp = Instant.now();
    }
    
    public String getQueryId() {
        return queryId;
    }
    
    public String getQueryType() {
        return queryType;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public String getResponseType() {
        return responseType;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "QueryMessage{" +
                "queryId='" + queryId + '\'' +
                ", queryType='" + queryType + '\'' +
                ", responseType='" + responseType + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}