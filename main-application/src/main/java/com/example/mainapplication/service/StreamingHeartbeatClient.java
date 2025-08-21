package com.example.mainapplication.service;

import com.example.grpc.common.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Combined heartbeat client that:
 * 1. Sends periodic unary heartbeats to update own health status
 * 2. Subscribes to server streaming health updates to monitor other instances
 * This provides both health reporting and health monitoring capabilities.
 */
@Service
public class StreamingHeartbeatClient {
    
    private static final Logger logger = LoggerFactory.getLogger(StreamingHeartbeatClient.class);
    
    @Value("${app.custom-server.grpc.host:localhost}")
    private String grpcHost;
    
    @Value("${app.custom-server.grpc.port:9060}")
    private int grpcPort;
    
    @Value("${spring.application.name:main-application}")
    private String serviceName;
    
    private ManagedChannel channel;
    private CommandHandlingServiceGrpc.CommandHandlingServiceStub asyncStub;
    private CommandHandlingServiceGrpc.CommandHandlingServiceBlockingStub blockingStub;
    private ScheduledExecutorService heartbeatScheduler;
    
    private final AtomicBoolean healthStreamActive = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    
    private String instanceId;
    
    @PostConstruct
    public void initialize() {
        this.instanceId = generateInstanceId();
        this.heartbeatScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "heartbeat-" + instanceId);
            t.setDaemon(true);
            return t;
        });
        
        initializeGrpcChannel();
        startHeartbeatReporting();
        startHealthMonitoring();
    }
    
    private void initializeGrpcChannel() {
        try {
            this.channel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(5, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxInboundMessageSize(4 * 1024 * 1024) // 4MB
                    .build();
            
            this.asyncStub = CommandHandlingServiceGrpc.newStub(channel);
            this.blockingStub = CommandHandlingServiceGrpc.newBlockingStub(channel);
            
            logger.info("Initialized gRPC channel to {}:{}", grpcHost, grpcPort);
            
        } catch (Exception e) {
            logger.error("Failed to initialize gRPC channel: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Start sending periodic unary heartbeats to report this instance's health.
     * This is the preferred method for instances to report their own health status.
     */
    private void startHeartbeatReporting() {
        logger.info("Starting heartbeat reporting for instance: {}", instanceId);
        
        // Send initial heartbeat immediately
        sendUnaryHeartbeat(HealthStatus.STARTING);
        
        // Schedule regular heartbeats every 30 seconds
        heartbeatScheduler.scheduleWithFixedDelay(() -> {
            if (!shuttingDown.get()) {
                sendUnaryHeartbeat(HealthStatus.HEALTHY);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Start subscribing to health stream to monitor other instances.
     * This allows this instance to be aware of the health of other services.
     */
    private void startHealthMonitoring() {
        logger.info("Starting health monitoring stream for instance: {}", instanceId);
        
        // Schedule the health stream subscription with a delay to ensure server is ready
        heartbeatScheduler.schedule(() -> {
            if (!shuttingDown.get()) {
                subscribeToHealthStream();
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * Send a unary heartbeat to report this instance's health status.
     */
    public void sendUnaryHeartbeat(HealthStatus status) {
        if (shuttingDown.get()) {
            return;
        }
        
        try {
            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .setServiceName(serviceName)
                    .setStatus(status)
                    .setTimestamp(System.currentTimeMillis())
                    .putMetadata("version", getClass().getPackage().getImplementationVersion() != null ? 
                            getClass().getPackage().getImplementationVersion() : "dev")
                    .build();
            
            HeartbeatResponse response = blockingStub.sendHeartbeat(request);
            
            if (response.getSuccess()) {
                logger.debug("Heartbeat sent successfully for instance: {}", instanceId);
            } else {
                logger.warn("Heartbeat failed for instance {}: {}", instanceId, response.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Failed to send heartbeat for instance {}: {}", instanceId, e.getMessage(), e);
        }
    }
    
    /**
     * Subscribe to the health stream to monitor other instances.
     */
    private void subscribeToHealthStream() {
        if (healthStreamActive.get() || shuttingDown.get()) {
            logger.debug("Health stream already active or shutting down");
            return;
        }
        
        try {
            logger.info("Subscribing to health stream for monitoring");
            
            HealthStreamRequest request = HealthStreamRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .build();
            
            StreamObserver<HealthStreamResponse> responseObserver = new StreamObserver<HealthStreamResponse>() {
                @Override
                public void onNext(HealthStreamResponse response) {
                    handleHealthUpdate(response);
                }
                
                @Override
                public void onError(Throwable throwable) {
                    logger.warn("Health stream error: {}", throwable.getMessage());
                    healthStreamActive.set(false);
                    
                    if (!shuttingDown.get()) {
                        // Attempt to reconnect after a delay
                        scheduleHealthStreamReconnect();
                    }
                }
                
                @Override
                public void onCompleted() {
                    logger.info("Health stream completed");
                    healthStreamActive.set(false);
                    
                    if (!shuttingDown.get()) {
                        scheduleHealthStreamReconnect();
                    }
                }
            };
            
            // Start the server streaming call
            asyncStub.healthStream(request, responseObserver);
            healthStreamActive.set(true);
            
            logger.info("Health stream subscription established successfully");
            
        } catch (Exception e) {
            logger.error("Failed to subscribe to health stream: {}", e.getMessage(), e);
            scheduleHealthStreamReconnect();
        }
    }
    
    /**
     * Handle health updates received from the server stream.
     */
    private void handleHealthUpdate(HealthStreamResponse response) {
        logger.debug("Received health update for instance {} - status: {}", 
                response.getInstanceId(), response.getStatus());
        
        // Process health updates from other instances
        // This could trigger actions like:
        // - Updating local health status cache
        // - Triggering failover logic
        // - Updating circuit breaker states
        // - Notifying monitoring systems
        
        if (response.getMetadataMap().containsKey("broadcast")) {
            logger.info("Health broadcast: Instance {} is {}", 
                    response.getInstanceId(), response.getStatus());
        }
        
        // Handle server commands if any
        if (response.getMetadataMap().containsKey("command")) {
            handleServerCommand(response);
        }
    }
    
    /**
     * Handle commands received from the server via health updates.
     */
    private void handleServerCommand(HealthStreamResponse response) {
        String command = response.getMetadataMap().get("command");
        
        switch (command) {
            case "reconnect":
                logger.info("Server requested health stream reconnection");
                reconnectHealthStream();
                break;
            case "shutdown":
                logger.info("Server requested graceful shutdown");
                gracefulShutdown();
                break;
            default:
                logger.debug("Unknown server command: {}", command);
        }
    }
    
    /**
     * Schedule a health stream reconnection attempt after a delay.
     */
    private void scheduleHealthStreamReconnect() {
        if (shuttingDown.get()) {
            return;
        }
        
        logger.info("Scheduling health stream reconnection in 15 seconds");
        
        heartbeatScheduler.schedule(() -> {
            if (!shuttingDown.get()) {
                logger.info("Attempting to reconnect health stream");
                subscribeToHealthStream();
            }
        }, 15, TimeUnit.SECONDS);
    }
    
    /**
     * Manually trigger a health stream reconnection.
     */
    public void reconnectHealthStream() {
        logger.info("Manually reconnecting health stream");
        healthStreamActive.set(false);
        subscribeToHealthStream();
    }
    
    /**
     * Check if the health stream is currently active.
     */
    public boolean isHealthStreamActive() {
        return healthStreamActive.get();
    }
    
    /**
     * Check if heartbeat reporting is working (channel is available).
     */
    public boolean isHeartbeatActive() {
        return channel != null && !channel.isShutdown() && !shuttingDown.get();
    }
    
    /**
     * Get the instance ID for this service.
     */
    public String getInstanceId() {
        return instanceId;
    }
    
    /**
     * Force send a heartbeat with a specific status.
     */
    public void forceHeartbeat(HealthStatus status) {
        sendUnaryHeartbeat(status);
    }
    
    /**
     * Graceful shutdown of the heartbeat client.
     */
    @PreDestroy
    public void gracefulShutdown() {
        logger.info("Shutting down streaming heartbeat client");
        shuttingDown.set(true);
        
        // Send final stopping heartbeat
        if (isHeartbeatActive()) {
            sendUnaryHeartbeat(HealthStatus.STOPPING);
            
            try {
                Thread.sleep(1000); // Give time for final heartbeat
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Shutdown scheduler
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Close gRPC channel
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        healthStreamActive.set(false);
        logger.info("Streaming heartbeat client shutdown completed");
    }
    
    /**
     * Generate a unique instance ID.
     */
    private String generateInstanceId() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            String port = System.getProperty("server.port", "8080");
            return serviceName + "-" + hostname + "-" + port + "-" + System.currentTimeMillis();
        } catch (Exception e) {
            logger.warn("Failed to get hostname, using fallback instance ID");
            return serviceName + "-" + System.currentTimeMillis();
        }
    }
}