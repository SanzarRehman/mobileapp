package com.example.axon.service;

import com.example.axon.AxonHandlerRegistry;
import com.example.grpc.common.*;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.shade.org.apache.avro.Schema;
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

import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Unified gRPC-based service for:
 * 1. Auto-discovering and registering ALL handler types (commands, queries, events) from AxonHandlerRegistry
 * 2. Maintaining health status through heartbeats
 * 3. Monitoring other instances via health streaming
 * 4. Auto-discovery and service management
 * 
 * This service consolidates all gRPC handler registration and health management.
 * Uses clean Redis architecture for efficient routing.
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
    private final PulsarDynamicConsumerService pulsarDynamicConsumerService;
    private final StreamingHeartbeatClient streamingHeartbeatClient;
    private final AxonHandlerRegistry axonHandlerRegistry;
    
    // Auto-discovered handler types from AxonHandlerRegistry
    private List<String> commandTypes;
    private List<String> queryTypes;
    private List<String> eventTypes;
    private Map<String, String> schemaMap = new HashMap<>();



    private volatile boolean registered = false;

    @Autowired
    public GrpcCommandHandlerRegistrationService(PulsarDynamicConsumerService pulsarDynamicConsumerService, StreamingHeartbeatClient streamingHeartbeatClient,
                                                 AxonHandlerRegistry axonHandlerRegistry) {
      this.pulsarDynamicConsumerService = pulsarDynamicConsumerService;
      this.instanceId = generateInstanceId();
        this.serviceHost = getLocalHostAddress();
        this.streamingHeartbeatClient = streamingHeartbeatClient;
        this.axonHandlerRegistry = axonHandlerRegistry;
    }

    /**
     * Auto-register ALL handler types when the application is ready.
     * This method waits for AxonHandlerRegistry to complete its inspection.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        logger.info("Application ready, auto-discovering and registering ALL handlers with custom server via gRPC...");
        
        CompletableFuture.runAsync(() -> {
            try {
                // Wait a bit for the custom server to be ready and AxonHandlerRegistry to complete
                Thread.sleep(6000);
                
                // Auto-discover handler types from AxonHandlerRegistry
                autoDiscoverHandlerTypes();
                
                // Register all discovered handlers
                autoRegisterAllHandlers();
                
                // Log streaming heartbeat status
                logger.info("All handlers registered successfully! Commands: {}, Queries: {}, Events: {}. Streaming heartbeats active: {}", 
                           commandTypes.size(), queryTypes.size(), eventTypes.size(),
                           streamingHeartbeatClient.isHealthStreamActive());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Registration interrupted during startup delay");
            } catch (Exception e) {
                logger.error("Failed to auto-register handlers on startup: {}", e.getMessage(), e);
                // Schedule retry
                scheduleRetryRegistration();
            }
        });
    }

    /**
     * Auto-discover all handler types from AxonHandlerRegistry.
     */
    private void autoDiscoverHandlerTypes() {
        logger.info("Auto-discovering handler types from AxonHandlerRegistry...");
        Map<String, Schema> eventClassToAvroSchema = new HashMap<>();
        eventClassToAvroSchema = axonHandlerRegistry.getEventClassToAvroSchema();
        for (Map.Entry<String, Schema> entry : eventClassToAvroSchema.entrySet()) {
            schemaMap.put(entry.getKey(), entry.getValue().toString()); // Avro Schema JSON
        }
        // Extract command types
        commandTypes = axonHandlerRegistry.getCommandHandlers().keySet().stream()
                .map(Class::getName)
                .collect(Collectors.toList());
        
        // Extract query types  
        queryTypes = axonHandlerRegistry.getQueryHandlers().keySet().stream()
                .map(Class::getName)
                .collect(Collectors.toList());
        
        // Extract event types
        eventTypes = axonHandlerRegistry.getEventHandlers().keySet().stream()
                .map(Class::getName)
                .collect(Collectors.toList());
                
        logger.info("Auto-discovered handlers - Commands: {}, Queries: {}, Events: {}", 
                   commandTypes.size(), queryTypes.size(), eventTypes.size());
        
        if (logger.isDebugEnabled()) {
            logger.debug("Command types: {}", commandTypes);
            logger.debug("Query types: {}", queryTypes);
            logger.debug("Event types: {}", eventTypes);
        }
    }

    /**
     * Auto-discover and register ALL handler types for this service instance.
     * Uses the new unified RegisterHandlers gRPC method.
     */
    private void autoRegisterAllHandlers() throws PulsarClientException {
        int totalHandlers = commandTypes.size() + queryTypes.size() + eventTypes.size();
        logger.info("Auto-registering {} total handlers for instance {} (Commands: {}, Queries: {}, Events: {})", 
                   totalHandlers, instanceId, commandTypes.size(), queryTypes.size(), eventTypes.size());

        try {
            // Create metadata for the service instance
            Map<String, String> metadata = new HashMap<>();
            metadata.put("application.name", applicationName);
            metadata.put("application.version", "1.0.0");
            metadata.put("startup.time", Instant.now().toString());
            metadata.put("registration.type", "auto-discovery");
            metadata.put("axon.handler.registry", "enabled");
            metadata.put("total.handlers", String.valueOf(totalHandlers));

            // Use the new unified RegisterHandlers method
            RegisterHandlersRequest request = RegisterHandlersRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .setServiceName(applicationName)
                    .setHost(serviceHost)
                    .setPort(serverPort)
                    .addAllCommandTypes(commandTypes)
                    .addAllQueryTypes(queryTypes)
                    .addAllEventTypes(eventTypes)
                    .putAllMetadata(metadata)
                    .putAllSchema(schemaMap)
                    .build();

            RegisterHandlersResponse response = commandHandlingStub.registerHandlers(request);

            if (response.getSuccess()) {


                registered = true;

                for(String cmd : eventTypes) {
                    String simpleName = cmd.substring(cmd.lastIndexOf('.') + 1);
                    pulsarDynamicConsumerService.subscribeToTopic(simpleName, applicationName);
                     logger.debug("Consumer added: {}", cmd);
                }

                HandlerRegistrationSummary summary = response.getSummary();
                logger.info("Successfully auto-registered ALL handlers for instance {}", instanceId);
                logger.info("Registration summary - Commands: {}, Queries: {}, Events: {}", 
                           summary.getCommandsRegistered(), 
                           summary.getQueriesRegistered(), 
                           summary.getEventsRegistered());
                logger.debug("Registration response: {}", response.getMessage());
            } else {
                logger.error("Failed to register handlers: {}", response.getMessage());
                throw new RuntimeException("Registration failed: " + response.getMessage());
            }

        } catch (StatusRuntimeException e) {
            logger.error("gRPC error during handler registration: {}", e.getStatus(), e);
            throw new RuntimeException("gRPC registration failed", e);
        } catch (Exception e) {
            logger.error("Unexpected error during handler registration: {}", e.getMessage(), e);
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
            metadata.put("total_handlers", String.valueOf(commandTypes.size() + queryTypes.size() + eventTypes.size()));

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

            // Test connectivity by discovering handlers for our own command types
            if (!commandTypes.isEmpty()) {
                DiscoverCommandHandlersRequest request = DiscoverCommandHandlersRequest.newBuilder()
                        .setCommandType(commandTypes.get(0))
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
     * Schedule a retry of handler registration.
     */
    private void scheduleRetryRegistration() {
        if (registered) {
            return; // Already registered, no need to retry
        }

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(10000); // Wait 10 seconds before retry
                autoRegisterAllHandlers();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Retry registration failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Unregister ALL handlers when the application shuts down.
     * StreamingHeartbeatClient will automatically handle its own cleanup.
     */
    @PreDestroy
    public void onShutdown() {
        if (!registered) {
            return;
        }

        logger.info("Application shutting down, unregistering ALL handlers...");
        
        try {
            // Force a final heartbeat with STOPPING status via StreamingHeartbeatClient
            streamingHeartbeatClient.forceHeartbeat(HealthStatus.STOPPING);
            
            // Use the new unified UnregisterHandlers method
            UnregisterHandlersRequest request = UnregisterHandlersRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .addAllCommandTypes(commandTypes)
                    .addAllQueryTypes(queryTypes)
                    .addAllEventTypes(eventTypes)
                    .build();

            UnregisterHandlersResponse response = commandHandlingStub.unregisterHandlers(request);

            if (response.getSuccess()) {
                HandlerRegistrationSummary summary = response.getSummary();
                logger.info("Successfully unregistered ALL handlers for instance {}", instanceId);
                logger.info("Unregistration summary - Commands: {}, Queries: {}, Events: {}", 
                           summary.getCommandsUnregistered(), 
                           summary.getQueriesUnregistered(), 
                           summary.getEventsUnregistered());
            } else {
                logger.warn("Failed to unregister handlers: {}", response.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error during shutdown unregistration: {}", e.getMessage());
        }
        
        logger.info("gRPC unified handler registration service shutdown completed");
    }

    /**
     * Get discovered handlers for ALL types.
     */
    public Map<String, Object> discoverAllHandlers() {
        Map<String, Object> allHandlers = new HashMap<>();
        
        try {
            // Discover command handlers
            if (!commandTypes.isEmpty()) {
                List<ServiceInstance> commandHandlers = commandTypes.stream()
                        .flatMap(commandType -> {
                            try {
                                DiscoverCommandHandlersRequest request = DiscoverCommandHandlersRequest.newBuilder()
                                        .setCommandType(commandType)
                                        .setOnlyHealthy(true)
                                        .build();
                                return commandHandlingStub.discoverCommandHandlers(request).getInstancesList().stream();
                            } catch (Exception e) {
                                logger.error("Failed to discover handlers for command type: {}", commandType, e);
                                return java.util.stream.Stream.empty();
                            }
                        })
                        .distinct()
                        .collect(Collectors.toList());
                allHandlers.put("commands", commandHandlers);
            }

            // Discover query handlers
            if (!queryTypes.isEmpty()) {
                List<ServiceInstance> queryHandlers = queryTypes.stream()
                        .flatMap(queryType -> {
                            try {
                                DiscoverQueryHandlersRequest request = DiscoverQueryHandlersRequest.newBuilder()
                                        .setQueryType(queryType)
                                        .setOnlyHealthy(true)
                                        .build();
                                return commandHandlingStub.discoverQueryHandlers(request).getInstancesList().stream();
                            } catch (Exception e) {
                                logger.error("Failed to discover handlers for query type: {}", queryType, e);
                                return java.util.stream.Stream.empty();
                            }
                        })
                        .distinct()
                        .collect(Collectors.toList());
                allHandlers.put("queries", queryHandlers);
            }

            // Discover event handlers
            if (!eventTypes.isEmpty()) {
                List<ServiceInstance> eventHandlers = eventTypes.stream()
                        .flatMap(eventType -> {
                            try {
                                DiscoverEventHandlersRequest request = DiscoverEventHandlersRequest.newBuilder()
                                        .setEventType(eventType)
                                        .setOnlyHealthy(true)
                                        .build();
                                return commandHandlingStub.discoverEventHandlers(request).getInstancesList().stream();
                            } catch (Exception e) {
                                logger.error("Failed to discover handlers for event type: {}", eventType, e);
                                return java.util.stream.Stream.empty();
                            }
                        })
                        .distinct()
                        .collect(Collectors.toList());
                allHandlers.put("events", eventHandlers);
            }

        } catch (Exception e) {
            logger.error("Failed to discover all handlers: {}", e.getMessage(), e);
        }
        
        return allHandlers;
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
    public void forceRegistration() throws PulsarClientException {
        registered = false;
        autoRegisterAllHandlers();
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
        return commandTypes;
    }

    /**
     * Check if the service is currently registered.
     */
    public boolean isRegistered() {
        return registered;
    }
}
