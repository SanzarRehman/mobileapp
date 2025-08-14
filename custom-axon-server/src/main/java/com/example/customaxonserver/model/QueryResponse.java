package com.example.customaxonserver.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents the response from query processing.
 */
public class QueryResponse {
    
    private final String queryId;
    private final String status;
    private final String targetInstance;
    private final Object result;
    private final String errorMessage;
    private final Instant timestamp;
    
    @JsonCreator
    public QueryResponse(
            @JsonProperty("queryId") String queryId,
            @JsonProperty("status") String status,
            @JsonProperty("targetInstance") String targetInstance,
            @JsonProperty("result") Object result,
            @JsonProperty("errorMessage") String errorMessage) {
        
        this.queryId = queryId;
        this.status = status;
        this.targetInstance = targetInstance;
        this.result = result;
        this.errorMessage = errorMessage;
        this.timestamp = Instant.now();
    }
    
    public static QueryResponse success(String queryId, String targetInstance, Object result) {
        return new QueryResponse(queryId, "SUCCESS", targetInstance, result, null);
    }
    
    public static QueryResponse routed(String queryId, String targetInstance) {
        return new QueryResponse(queryId, "ROUTED", targetInstance, null, null);
    }
    
    public static QueryResponse error(String queryId, String errorMessage) {
        return new QueryResponse(queryId, "ERROR", null, null, errorMessage);
    }
    
    public String getQueryId() {
        return queryId;
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
        return "QueryResponse{" +
                "queryId='" + queryId + '\'' +
                ", status='" + status + '\'' +
                ", targetInstance='" + targetInstance + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}