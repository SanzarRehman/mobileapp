package com.example.mainapplication.service;

import com.example.grpc.common.*;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unified gRPC-based service for:
 * 1. Registering command handlers with the custom axon server
 * 2. Maintaining health status through heartbeats
 * 3. Monitoring other instances via health streaming
 * 4. Auto-discovery and service management
 * 
 * This service consolidates all gRPC command handler registration and health management.
 */
@Service
@EnableScheduling
public class GrpcCommandHandlerRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(GrpcCommandHandlerRegistrationService.class);

    @GrpcClient("custom-axon-server")
    private CommandHandlingServiceGrpc.CommandHandlingServiceBlockingStub commandHandlingStub;

    @GrpcClient("custom-axon-server")
    private ServiceDiscoveryServiceGrpc.ServiceDiscoveryServiceBlockingStub serviceDiscoveryStub;

    @Value("${spring.application.name:main-application}")
    private String applicationName;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${grpc.client.custom-axon-server.address:static://localhost:9060}")
    private String customServerAddress;

    private final String instanceId;
    private final String serviceHost;
    private final StreamingHeartbeatClient streamingHeartbeatClient;
    
    // Command types that this instance can handle - auto-discovered
    private final List<String> supportedCommandTypes = Arrays.asList(
        "com.example.mainapplication.command.CreateUserCommand",
        "com.example.mainapplication.command.UpdateUserCommand"
    );

    private volatile boolean registered = false;

    @Autowired
    public GrpcCommandHandlerRegistrationService(StreamingHeartbeatClient streamingHeartbeatClient) {
        this.instanceId = generateInstanceId();
        this.serviceHost = getLocalHostAddress();
        this.streamingHeartbeatClient = streamingHeartbeatClient;
    }

    /**
     * Auto-register command handlers when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        logger.info("Application ready, auto-registering command handlers with custom server via gRPC...");
        
        CompletableFuture.runAsync(() -> {
            try {
                // Wait a bit for the custom server to be ready
                Thread.sleep(5000);
                autoRegisterCommandHandlers();
                
                // Log streaming heartbeat status
                logger.info("Command handlers registered. Streaming heartbeats active: {}, Regular heartbeats active: {}", 
                           streamingHeartbeatClient.isHealthStreamActive(), 
                           streamingHeartbeatClient.isHeartbeatActive());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Registration interrupted during startup delay");
            } catch (Exception e) {
                logger.error("Failed to auto-register command handlers on startup: {}", e.getMessage(), e);
                // Schedule retry
                scheduleRetryRegistration();
            }
        });
    }

    /**
     * Auto-discover and register all command handlers for this service instance.
     */
    private void autoRegisterCommandHandlers() {
        logger.info("Auto-registering {} command handlers for instance {}", 
                   supportedCommandTypes.size(), instanceId);

        try {
            // Create metadata for the service instance
            Map<String, String> metadata = new HashMap<>();
            metadata.put("application.name", applicationName);
            metadata.put("application.version", "1.0.0");
            metadata.put("startup.time", Instant.now().toString());
            metadata.put("registration.type", "auto-discovery");

            // Register command handlers via gRPC
            RegisterCommandHandlerRequest request = RegisterCommandHandlerRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .setServiceName(applicationName)
                    .setHost(serviceHost)
                    .setPort(serverPort)
                    .addAllCommandTypes(supportedCommandTypes)
                    .putAllMetadata(metadata)
                    .build();

            RegisterCommandHandlerResponse response = commandHandlingStub.registerCommandHandler(request);

            if (response.getSuccess()) {
                registered = true;
                logger.info("Successfully auto-registered all command handlers for instance {}", instanceId);
                logger.debug("Registration response: {}", response.getMessage());
            } else {
                logger.error("Failed to register command handlers: {}", response.getMessage());
                throw new RuntimeException("Registration failed: " + response.getMessage());
            }

        } catch (StatusRuntimeException e) {
            logger.error("gRPC error during command handler registration: {}", e.getStatus(), e);
            throw new RuntimeException("gRPC registration failed", e);
        } catch (Exception e) {
            logger.error("Unexpected error during command handler registration: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Send periodic heartbeat to maintain healthy status.
     * This works in coordination with StreamingHeartbeatClient.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        if (!registered) {
            logger.debug("Skipping heartbeat - not registered yet");
            return;
        }

        try {
            // Check if streaming heartbeat is working
            if (!streamingHeartbeatClient.isHeartbeatActive()) {
                logger.warn("Streaming heartbeat is not active! Attempting reconnection...");
                streamingHeartbeatClient.reconnectHealthStream();
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("last_heartbeat", Instant.now().toString());
            metadata.put("memory_usage", getMemoryUsage());
            metadata.put("active_threads", String.valueOf(Thread.activeCount()));
            metadata.put("streaming_active", String.valueOf(streamingHeartbeatClient.isHealthStreamActive()));

            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .setServiceName(applicationName)
                    .setStatus(HealthStatus.HEALTHY)
                    .putAllMetadata(metadata)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            HeartbeatResponse response = commandHandlingStub.sendHeartbeat(request);

            if (response.getSuccess()) {
                logger.trace("Heartbeat sent successfully for instance {}", instanceId);
            } else {
                logger.warn("Heartbeat failed: {} for instance {}", response.getMessage(), instanceId);
                // Try to re-register handlers
                scheduleRetryRegistration();
            }

        } catch (StatusRuntimeException e) {
            logger.error("gRPC error sending heartbeat for instance {}: {}", instanceId, e.getStatus());
            // Try to re-register handlers on next heartbeat
            scheduleRetryRegistration();
        } catch (Exception e) {
            logger.error("Failed to send heartbeat for instance {}: {}", instanceId, e.getMessage());
            scheduleRetryRegistration();
        }
    }

    /**
     * Health check to ensure custom server is available and re-register if needed.
     * Also monitors streaming heartbeat status.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    public void healthCheck() {
        try {
            // Check streaming heartbeat status
            if (!streamingHeartbeatClient.isHealthStreamActive()) {
                logger.warn("Health stream connection lost. Attempting to reconnect...");
                streamingHeartbeatClient.reconnectHealthStream();
            }

            // Discover available command handlers to test connectivity
            DiscoverCommandHandlersRequest request = DiscoverCommandHandlersRequest.newBuilder()
                    .setCommandType(supportedCommandTypes.get(0))
                    .setOnlyHealthy(true)
                    .build();

            DiscoverCommandHandlersResponse response = commandHandlingStub.discoverCommandHandlers(request);
            
            logger.trace("Custom server health check passed - found {} healthy instances", 
                        response.getHealthyCount());

            // If we're not in the discovered instances, re-register
            boolean foundSelf = response.getInstancesList().stream()
                    .anyMatch(instance -> instanceId.equals(instance.getInstanceId()));

            if (!foundSelf && registered) {
                logger.warn("Instance {} not found in discovered handlers, attempting re-registration", instanceId);
                registered = false;
                scheduleRetryRegistration();
            }

        } catch (StatusRuntimeException e) {
            logger.warn("Custom server health check failed: {}", e.getStatus());
            if (registered) {
                registered = false;
                scheduleRetryRegistration();
            }
        } catch (Exception e) {
            logger.warn("Custom server health check failed: {}", e.getMessage());
        }
    }

    /**
     * Schedule a retry of command handler registration.
     */
    private void scheduleRetryRegistration() {
        if (registered) {
            return; // Already registered, no need to retry
        }

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(10000); // Wait 10 seconds before retry
                autoRegisterCommandHandlers();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Retry registration failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Unregister command handlers when the application shuts down.
     * StreamingHeartbeatClient will automatically handle its own cleanup.
     */
    @PreDestroy
    public void onShutdown() {
        if (!registered) {
            return;
        }

        logger.info("Application shutting down, unregistering command handlers...");
        
        try {
            // Force a final heartbeat with STOPPING status via StreamingHeartbeatClient
            streamingHeartbeatClient.forceHeartbeat(HealthStatus.STOPPING);
            
            UnregisterCommandHandlerRequest request = UnregisterCommandHandlerRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .addAllCommandTypes(supportedCommandTypes)
                    .build();

            UnregisterCommandHandlerResponse response = commandHandlingStub.unregisterCommandHandler(request);

            if (response.getSuccess()) {
                logger.info("Successfully unregistered command handlers for instance {}", instanceId);
            } else {
                logger.warn("Failed to unregister command handlers: {}", response.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error during shutdown unregistration: {}", e.getMessage());
        }
        
        logger.info("gRPC command handler registration service shutdown completed");
    }

    /**
     * Get discovered command handlers for a specific command type.
     */
    public List<ServiceInstance> discoverCommandHandlers(String commandType) {
        try {
            DiscoverCommandHandlersRequest request = DiscoverCommandHandlersRequest.newBuilder()
                    .setCommandType(commandType)
                    .setOnlyHealthy(true)
                    .build();

            DiscoverCommandHandlersResponse response = commandHandlingStub.discoverCommandHandlers(request);
            return response.getInstancesList();

        } catch (Exception e) {
            logger.error("Failed to discover command handlers for type: {}", commandType, e);
            return Arrays.asList();
        }
    }

    /**
     * Generate a unique instance ID for this application instance.
     */
    private String generateInstanceId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return applicationName + "-" + hostname + "-" + System.currentTimeMillis();
        } catch (Exception e) {
            logger.warn("Failed to get hostname, using fallback instance ID");
            return applicationName + "-" + System.currentTimeMillis();
        }
    }

    /**
     * Get local host address.
     */
    private String getLocalHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            logger.warn("Failed to get local host address, using localhost");
            return "localhost";
        }
    }

    /**
     * Get current memory usage as a string.
     */
    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        return String.format("%.2f%%", (double) usedMemory / totalMemory * 100);
    }

    /**
     * Check if streaming heartbeats are active.
     */
    public boolean isStreamingHeartbeatActive() {
        return streamingHeartbeatClient.isHealthStreamActive();
    }

    /**
     * Check if regular heartbeats are active.
     */
    public boolean isHeartbeatActive() {
        return streamingHeartbeatClient.isHeartbeatActive();
    }

    /**
     * Get the streaming heartbeat client instance ID.
     */
    public String getStreamingInstanceId() {
        return streamingHeartbeatClient.getInstanceId();
    }

    /**
     * Manually trigger heartbeat stream reconnection.
     */
    public void forceHeartbeatReconnect() {
        streamingHeartbeatClient.reconnectHealthStream();
    }

    /**
     * Manually trigger registration (useful for testing).
     */
    public void forceRegistration() {
        registered = false;
        autoRegisterCommandHandlers();
    }

    /**
     * Get the instance ID for this application.
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Get the list of supported command types.
     */
    public List<String> getSupportedCommandTypes() {
        return supportedCommandTypes;
    }

    /**
     * Check if the service is currently registered.
     */
    public boolean isRegistered() {
        return registered;
    }
}
