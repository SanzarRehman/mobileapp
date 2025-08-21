package com.example.mainapplication.service;

import com.example.grpc.common.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for registering the main application as a command handler
 * with the custom axon server and maintaining health status using streaming heartbeats.
 * 
 * This service now uses gRPC for command handler registration and StreamingHeartbeatClient 
 * for efficient heartbeat communication.
 */
@Service
@EnableScheduling
public class CommandHandlerRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandlerRegistrationService.class);

    private final RestTemplate restTemplate;
    private final String customServerUrl;
    private final String instanceId;
    private final StreamingHeartbeatClient streamingHeartbeatClient;
    
    // gRPC components for command handler registration
    private final String grpcHost;
    private final int grpcPort;
    private ManagedChannel grpcChannel;
    private CommandHandlingServiceGrpc.CommandHandlingServiceBlockingStub blockingStub;
    
    // Command types that this instance can handle
    private final List<String> supportedCommandTypes = Arrays.asList(
        "com.example.mainapplication.command.CreateUserCommand",
        "com.example.mainapplication.command.UpdateUserCommand"
    );

    @Autowired
    public CommandHandlerRegistrationService(
            @Qualifier("restTemplate") RestTemplate restTemplate,
            @Value("${app.custom-server.url:http://localhost:8081}") String customServerUrl,
            @Value("${spring.application.name:main-application}") String applicationName,
            @Value("${app.custom-server.grpc.host:localhost}") String grpcHost,
            @Value("${app.custom-server.grpc.port:9060}") int grpcPort,
            StreamingHeartbeatClient streamingHeartbeatClient) {
        this.restTemplate = restTemplate;
        this.customServerUrl = customServerUrl;
        this.instanceId = generateInstanceId(applicationName);
        this.streamingHeartbeatClient = streamingHeartbeatClient;
        this.grpcHost = grpcHost;
        this.grpcPort = grpcPort;
        
        initializeGrpcChannel();
    }
    
    /**
     * Initialize gRPC channel for command handler registration.
     */
    private void initializeGrpcChannel() {
        try {
            this.grpcChannel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(5, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxInboundMessageSize(4 * 1024 * 1024) // 4MB
                    .build();
            
            this.blockingStub = CommandHandlingServiceGrpc.newBlockingStub(grpcChannel);
            
            logger.info("Initialized gRPC channel for registration to {}:{}", grpcHost, grpcPort);
            
        } catch (Exception e) {
            logger.error("Failed to initialize gRPC channel for registration: {}", e.getMessage(), e);
        }
    }

    /**
     * Register command handlers when the application is ready.
     * The streaming heartbeat client will be automatically initialized.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        logger.info("Application ready, registering command handlers with custom server...");
        
        CompletableFuture.runAsync(() -> {
            try {
                // Wait a bit for the custom server to be ready
                Thread.sleep(5000);
                registerAllCommandHandlers();
                
                // The StreamingHeartbeatClient will automatically start sending heartbeats
                logger.info("Command handlers registered. Streaming heartbeats are active: {}", 
                           streamingHeartbeatClient.isHealthStreamActive());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Registration interrupted during startup delay");
            } catch (Exception e) {
                logger.error("Failed to register command handlers on startup: {}", e.getMessage(), e);
                // Schedule retry
                scheduleRetryRegistration();
            }
        });
    }

    /**
     * Register all supported command handlers with the custom server.
     */
    private void registerAllCommandHandlers() {
        logger.info("Registering {} command handlers for instance {}", 
                   supportedCommandTypes.size(), instanceId);

        boolean allRegistered = true;
        for (String commandType : supportedCommandTypes) {
            try {
                registerCommandHandler(commandType);
                logger.debug("Successfully registered handler for: {}", commandType);
            } catch (Exception e) {
                logger.error("Failed to register handler for {}: {}", commandType, e.getMessage());
                allRegistered = false;
            }
        }

        if (allRegistered) {
            logger.info("All command handlers registered successfully for instance {}", instanceId);
        } else {
            logger.warn("Some command handlers failed to register. Will retry on next health check.");
        }
    }

    /**
     * Register a single command handler with the custom server using gRPC.
     */
    private void registerCommandHandler(String commandType) {
        try {
            logger.debug("Registering command handler for {} via gRPC", commandType);
            
            RegisterCommandHandlerRequest request = RegisterCommandHandlerRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .setServiceName("main-application")
                    .setHost("localhost") // TODO: Get actual host
                    .setPort(8080) // TODO: Get actual port from server.port
                    .addCommandTypes(commandType)
                    .putMetadata("version", "1.0.0")
                    .putMetadata("registered_at", String.valueOf(System.currentTimeMillis()))
                    .build();
            
            RegisterCommandHandlerResponse response = blockingStub.registerCommandHandler(request);
            
            if (response.getSuccess()) {
                logger.info("Successfully registered handler for {} on instance {}", commandType, instanceId);
            } else {
                logger.error("Failed to register handler for {}: {}", commandType, response.getMessage());
                throw new RuntimeException("Registration failed: " + response.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Failed to register handler for {} on instance {}: {}", 
                    commandType, instanceId, e.getMessage());
            throw e;
        }
    }

    /**
     * DEPRECATED: Individual heartbeat sending is replaced by streaming heartbeats.
     * The StreamingHeartbeatClient automatically maintains the heartbeat stream.
     */
    @Deprecated
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        // This method is now deprecated and replaced by StreamingHeartbeatClient
        // Check if streaming heartbeat is active
        if (!streamingHeartbeatClient.isHeartbeatActive()) {
            logger.warn("Streaming heartbeat is not active! Instance may appear unhealthy.");
            // Attempt to restart the stream
            streamingHeartbeatClient.reconnectHealthStream();
        } else {
            logger.trace("Streaming heartbeat is active for instance {}", instanceId);
        }
    }

    /**
     * Monitor custom server availability and streaming heartbeat status.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    public void healthCheck() {
        try {
            // Check if streaming heartbeat is working
            if (!streamingHeartbeatClient.isHeartbeatActive()) {
                logger.warn("Streaming heartbeat connection lost. Attempting to reconnect...");
                streamingHeartbeatClient.reconnectHealthStream();
            }
            
            // Optional: Check custom server HTTP health
            String healthUrl = customServerUrl + "/actuator/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.trace("Custom server HTTP health check passed");
            } else {
                logger.warn("Custom server HTTP health check failed: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.warn("Health check failed: {}", e.getMessage());
        }
    }

    /**
     * Schedule a retry of command handler registration.
     */
    private void scheduleRetryRegistration() {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(10000); // Wait 10 seconds before retry
                registerAllCommandHandlers();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Retry registration failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Unregister command handlers when the application shuts down.
     * The StreamingHeartbeatClient will automatically send a STOPPING status.
     */
    @PreDestroy
    public void onShutdown() {
        logger.info("Application shutting down, cleaning up command handler registration...");
        
        // The StreamingHeartbeatClient will automatically send STOPPING status
        // and close the stream in its @PreDestroy method
        
        for (String commandType : supportedCommandTypes) {
            try {
                unregisterCommandHandler(commandType);
            } catch (Exception e) {
                logger.warn("Failed to unregister handler for {}: {}", commandType, e.getMessage());
            }
        }
        
        // Close gRPC channel
        if (grpcChannel != null && !grpcChannel.isShutdown()) {
            grpcChannel.shutdown();
            try {
                if (!grpcChannel.awaitTermination(5, TimeUnit.SECONDS)) {
                    grpcChannel.shutdownNow();
                }
            } catch (InterruptedException e) {
                grpcChannel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Command handler cleanup completed");
    }

    /**
     * Unregister a command handler from the custom server using gRPC.
     */
    private void unregisterCommandHandler(String commandType) {
        try {
            logger.debug("Unregistering command handler for {} via gRPC", commandType);
            
            UnregisterCommandHandlerRequest request = UnregisterCommandHandlerRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .addCommandTypes(commandType)
                    .build();
            
            UnregisterCommandHandlerResponse response = blockingStub.unregisterCommandHandler(request);
            
            if (response.getSuccess()) {
                logger.info("Successfully unregistered handler for {} on instance {}", commandType, instanceId);
            } else {
                logger.warn("Failed to unregister handler for {}: {}", commandType, response.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Failed to unregister handler for {} on instance {}: {}", 
                    commandType, instanceId, e.getMessage());
        }
    }

    /**
     * Generate a unique instance ID for this application instance.
     */
    private String generateInstanceId(String applicationName) {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String port = System.getProperty("server.port", "8080");
            return applicationName + "-" + hostname + "-" + port + "-" + System.currentTimeMillis();
        } catch (Exception e) {
            logger.warn("Failed to get hostname, using fallback instance ID");
            return applicationName + "-" + System.currentTimeMillis();
        }
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
     * Check if streaming heartbeats are active.
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
     * Manually trigger registration (useful for testing).
     */
    public void forceRegistration() {
        registerAllCommandHandlers();
    }

    /**
     * Manually trigger heartbeat stream reconnection.
     */
    public void forceHeartbeatReconnect() {
        streamingHeartbeatClient.reconnectHealthStream();
    }
}