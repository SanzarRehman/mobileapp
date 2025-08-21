package com.example.customaxonserver.grpc.service;

import com.example.grpc.common.*;
import com.example.customaxonserver.service.CommandRoutingService;
import com.example.customaxonserver.service.ServiceDiscoveryService;
import com.example.customaxonserver.service.StreamingHeartbeatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * gRPC service implementation for command handling operations.
 * Provides auto-discovery, registration, and command routing capabilities.
 * Now integrated with StreamingHeartbeatService for efficient health management.
 */
@GrpcService
public class CommandHandlingServiceImpl extends CommandHandlingServiceGrpc.CommandHandlingServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandlingServiceImpl.class);

    private final CommandRoutingService commandRoutingService;
    private final ServiceDiscoveryService serviceDiscoveryService;
    private final StreamingHeartbeatService streamingHeartbeatService;
    private final ObjectMapper objectMapper;
    
    // Store active health streams for real-time updates
    private final ConcurrentMap<String, StreamObserver<HealthStreamResponse>> healthStreams = new ConcurrentHashMap<>();

    @Autowired
    public CommandHandlingServiceImpl(CommandRoutingService commandRoutingService,
                                    ServiceDiscoveryService serviceDiscoveryService,
                                    StreamingHeartbeatService streamingHeartbeatService,
                                    ObjectMapper objectMapper) {
        this.commandRoutingService = commandRoutingService;
        this.serviceDiscoveryService = serviceDiscoveryService;
        this.streamingHeartbeatService = streamingHeartbeatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerCommandHandler(RegisterCommandHandlerRequest request,
                                     StreamObserver<RegisterCommandHandlerResponse> responseObserver) {
        logger.info("Registering command handler for instance: {} with commands: {}", 
                   request.getInstanceId(), request.getCommandTypesList());
        
        try {
            // Create service instance
            ServiceInstance serviceInstance = ServiceInstance.newBuilder()
                    .setInstanceId(request.getInstanceId())
                    .setServiceName(request.getServiceName())
                    .setHost(request.getHost())
                    .setPort(request.getPort())
                    .setStatus(HealthStatus.HEALTHY)
                    .addAllCommandTypes(request.getCommandTypesList())
                    .putAllMetadata(request.getMetadataMap())
                    .setLastHeartbeat(Instant.now().toEpochMilli())
                    .setVersion("1.0.0")
                    .build();

            // Register with service discovery
            serviceDiscoveryService.registerService(serviceInstance);

            // Register each command handler
            for (String commandType : request.getCommandTypesList()) {
                commandRoutingService.registerCommandHandler(request.getInstanceId(), commandType);
            }

            RegisterCommandHandlerResponse response = RegisterCommandHandlerResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Command handlers registered successfully")
                    .setRegistrationId(request.getInstanceId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info("Successfully registered command handlers for instance: {}", request.getInstanceId());

        } catch (Exception e) {
            logger.error("Failed to register command handlers for instance: {}", request.getInstanceId(), e);
            
            RegisterCommandHandlerResponse response = RegisterCommandHandlerResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to register command handlers: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void unregisterCommandHandler(UnregisterCommandHandlerRequest request,
                                       StreamObserver<UnregisterCommandHandlerResponse> responseObserver) {
        logger.info("Unregistering command handlers for instance: {} with commands: {}", 
                   request.getInstanceId(), request.getCommandTypesList());
        
        try {
            // Unregister each command handler
            for (String commandType : request.getCommandTypesList()) {
                commandRoutingService.unregisterCommandHandler(request.getInstanceId(), commandType);
            }

            // Unregister from service discovery if no more command types
            if (commandRoutingService.getCommandTypesForInstance(request.getInstanceId()).isEmpty()) {
                serviceDiscoveryService.unregisterService(request.getInstanceId());
            }

            UnregisterCommandHandlerResponse response = UnregisterCommandHandlerResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Command handlers unregistered successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info("Successfully unregistered command handlers for instance: {}", request.getInstanceId());

        } catch (Exception e) {
            logger.error("Failed to unregister command handlers for instance: {}", request.getInstanceId(), e);
            
            UnregisterCommandHandlerResponse response = UnregisterCommandHandlerResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to unregister command handlers: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void submitCommand(SubmitCommandRequest request, StreamObserver<SubmitCommandResponse> responseObserver) {
        logger.debug("Submitting command: {} for aggregate: {}", request.getCommandType(), request.getAggregateId());
        
        try {
            // Route command to appropriate instance
            String targetInstance = commandRoutingService.routeCommand(request.getCommandType(), request.getAggregateId());
            
            // Get service instance details
            ServiceInstance serviceInstance = serviceDiscoveryService.getServiceInstance(targetInstance);
            
            if (serviceInstance == null) {
                throw new RuntimeException("Target instance not found: " + targetInstance);
            }

            // Forward command to target instance (this would be implemented via gRPC client call)
            // For now, we'll simulate the response
            SubmitCommandResponse response = SubmitCommandResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Command submitted successfully")
                    .setResult("Command processed")
                    .setTargetInstance(targetInstance)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.debug("Successfully routed command {} to instance: {}", request.getCommandId(), targetInstance);

        } catch (Exception e) {
            logger.error("Failed to submit command: {}", request.getCommandId(), e);
            
            SubmitCommandResponse response = SubmitCommandResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to submit command: " + e.getMessage())
                    .setErrorCode("ROUTING_ERROR")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void sendHeartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        logger.trace("Received heartbeat from instance: {} with status: {}", 
                    request.getInstanceId(), request.getStatus());
        
        try {
            // Update instance health using StreamingHeartbeatService
            streamingHeartbeatService.updateInstanceHealth(request.getInstanceId(), request.getStatus());
            
            // Also update service discovery
            serviceDiscoveryService.updateServiceHealth(request.getInstanceId(), request.getStatus());

            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Heartbeat received")
                    .setNextHeartbeatInterval(30) // 30 seconds
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to process heartbeat from instance: {}", request.getInstanceId(), e);
            
            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to process heartbeat: " + e.getMessage())
                    .setNextHeartbeatInterval(30)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void discoverCommandHandlers(DiscoverCommandHandlersRequest request,
                                      StreamObserver<DiscoverCommandHandlersResponse> responseObserver) {
        logger.debug("Discovering command handlers for command type: {}", request.getCommandType());
        
        try {
            List<String> instanceIds = commandRoutingService.getInstancesForCommandType(request.getCommandType());
            DiscoverCommandHandlersResponse.Builder responseBuilder = DiscoverCommandHandlersResponse.newBuilder();
            
            int healthyCount = 0;
            for (String instanceId : instanceIds) {
                ServiceInstance serviceInstance = serviceDiscoveryService.getServiceInstance(instanceId);
                if (serviceInstance != null) {
                    if (!request.getOnlyHealthy() || serviceInstance.getStatus() == HealthStatus.HEALTHY) {
                        responseBuilder.addInstances(serviceInstance);
                        if (serviceInstance.getStatus() == HealthStatus.HEALTHY) {
                            healthyCount++;
                        }
                    }
                }
            }

            DiscoverCommandHandlersResponse response = responseBuilder
                    .setTotalCount(instanceIds.size())
                    .setHealthyCount(healthyCount)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.debug("Found {} instances for command type: {}, {} healthy", 
                        instanceIds.size(), request.getCommandType(), healthyCount);

        } catch (Exception e) {
            logger.error("Failed to discover command handlers for type: {}", request.getCommandType(), e);
            
            DiscoverCommandHandlersResponse response = DiscoverCommandHandlersResponse.newBuilder()
                    .setTotalCount(0)
                    .setHealthyCount(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void healthStream(HealthStreamRequest request, StreamObserver<HealthStreamResponse> responseObserver) {
        logger.info("Starting health stream for instance: {}", request.getInstanceId());
        
        try {
            // Delegate to StreamingHeartbeatService for proper stream management
            streamingHeartbeatService.registerHealthStreamClient(request.getInstanceId(), responseObserver);
            
            logger.info("Health stream established for instance: {}", request.getInstanceId());
            
        } catch (Exception e) {
            logger.error("Failed to establish health stream for instance {}: {}", 
                    request.getInstanceId(), e.getMessage(), e);
            responseObserver.onError(e);
        }
    }
}