package com.example.customaxonserver.dto;

import com.example.grpc.common.HealthStatus;
import com.example.grpc.common.ServiceInstance;
import com.example.grpc.common.HealthStreamResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO for serializing ServiceInstance to JSON for Redis storage.
 * This avoids issues with Protocol Buffer serialization.
 */
public class ServiceInstanceDto {
    private String instanceId;
    private String serviceName;
    private String host;
    private int port;
    private String status; // Store as string to avoid enum serialization issues
    private List<String> commandTypes = new ArrayList<>();
    private Map<String, String> metadata = new HashMap<>();
    private long lastHeartbeat;
    private String version;
    private List<String> tags = new ArrayList<>();

    // Default constructor for Jackson
    public ServiceInstanceDto() {}

    // Constructor from Protocol Buffer ServiceInstance
    public ServiceInstanceDto(ServiceInstance serviceInstance) {
        this.instanceId = serviceInstance.getInstanceId();
        this.serviceName = serviceInstance.getServiceName();
        this.host = serviceInstance.getHost();
        this.port = serviceInstance.getPort();
        this.status = serviceInstance.getStatus().name();
        this.commandTypes = new ArrayList<>(serviceInstance.getCommandTypesList());
        this.metadata = new HashMap<>(serviceInstance.getMetadataMap());
        this.lastHeartbeat = serviceInstance.getLastHeartbeat();
        this.version = serviceInstance.getVersion();
        this.tags = new ArrayList<>(serviceInstance.getTagsList());
    }

    // Convert to Protocol Buffer ServiceInstance
    public ServiceInstance toServiceInstance() {
        HealthStatus healthStatus;
        try {
            healthStatus = HealthStatus.valueOf(this.status);
        } catch (IllegalArgumentException e) {
            healthStatus = HealthStatus.UNKNOWN;
        }

        return ServiceInstance.newBuilder()
                .setInstanceId(this.instanceId)
                .setServiceName(this.serviceName)
                .setHost(this.host)
                .setPort(this.port)
                .setStatus(healthStatus)
                .addAllCommandTypes(this.commandTypes)
                .putAllMetadata(this.metadata)
                .setLastHeartbeat(this.lastHeartbeat)
                .setVersion(this.version != null ? this.version : "")
                .addAllTags(this.tags)
                .build();
    }

    /**
     * Create a HealthStreamResponse from this instance for streaming heartbeats.
     * This is used for the streaming heartbeat mechanism.
     */
    public HealthStreamResponse toHealthStreamResponse() {
        HealthStatus healthStatus;
        try {
            healthStatus = HealthStatus.valueOf(this.status);
        } catch (IllegalArgumentException e) {
            healthStatus = HealthStatus.UNKNOWN;
        }

        return HealthStreamResponse.newBuilder()
                .setInstanceId(this.instanceId)
                .setStatus(healthStatus)
                .setTimestamp(System.currentTimeMillis())
                .putAllMetadata(this.metadata)
                .build();
    }

    /**
     * Update the heartbeat timestamp and status from a streaming heartbeat.
     */
    public void updateFromHealthStream(HealthStreamResponse healthStream) {
        this.status = healthStream.getStatus().name();
        this.lastHeartbeat = healthStream.getTimestamp();
        // Update metadata if provided
        if (!healthStream.getMetadataMap().isEmpty()) {
            this.metadata.putAll(healthStream.getMetadataMap());
        }
    }

    // Getters and setters
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getCommandTypes() {
        return commandTypes;
    }

    public void setCommandTypes(List<String> commandTypes) {
        this.commandTypes = commandTypes;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}