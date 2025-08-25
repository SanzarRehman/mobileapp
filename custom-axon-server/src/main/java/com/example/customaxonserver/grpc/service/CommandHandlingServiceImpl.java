package com.example.customaxonserver.grpc.service;

import com.example.grpc.common.*;
import com.example.customaxonserver.service.CommandRoutingService;
import com.example.customaxonserver.service.QueryRoutingService;
import com.example.customaxonserver.service.EventRoutingService;
import com.example.customaxonserver.service.ServiceDiscoveryService;
import com.example.customaxonserver.service.StreamingHeartbeatService;
import com.example.customaxonserver.util.pulser.PulsarProducerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Enhanced gRPC service implementation for all Axon handler types.
 * Provides auto-discovery, registration, and routing capabilities for:
 * - Command Handlers
 * - Query Handlers  
 * - Event Handlers
 * 
 * Integrated with StreamingHeartbeatService and clean Redis architecture.
 */
@GrpcService
@Component
public class CommandHandlingServiceImpl extends CommandHandlingServiceGrpc.CommandHandlingServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandlingServiceImpl.class);

    private final CommandRoutingService commandRoutingService;
    private final QueryRoutingService queryRoutingService;
    private final EventRoutingService eventRoutingService;
    private final ServiceDiscoveryService serviceDiscoveryService;
    private final StreamingHeartbeatService streamingHeartbeatService;
    private final ObjectMapper objectMapper;
    private final PulsarProducerFactory pulsarProducerFactory;
    // Store active health streams for real-time updates
    private final ConcurrentMap<String, StreamObserver<HealthStreamResponse>> healthStreams = new ConcurrentHashMap<>();

    @Autowired
    public CommandHandlingServiceImpl(CommandRoutingService commandRoutingService,
                                      QueryRoutingService queryRoutingService,
                                      EventRoutingService eventRoutingService,
                                      ServiceDiscoveryService serviceDiscoveryService,
                                      StreamingHeartbeatService streamingHeartbeatService,
                                      ObjectMapper objectMapper, PulsarProducerFactory pulsarProducerFactory) {
        this.commandRoutingService = commandRoutingService;
        this.queryRoutingService = queryRoutingService;
        this.eventRoutingService = eventRoutingService;
        this.serviceDiscoveryService = serviceDiscoveryService;
        this.streamingHeartbeatService = streamingHeartbeatService;
        this.objectMapper = objectMapper;
      this.pulsarProducerFactory = pulsarProducerFactory;
    }

    /**
     * Enhanced registration for all handler types (commands, queries, events).
     * This is the new unified registration method.
     */
    @Override
    public void registerHandlers(RegisterHandlersRequest request, StreamObserver<RegisterHandlersResponse> responseObserver) {
        logger.info("Registering all handlers for instance: {} with {} commands, {} queries, {} events",
                   request.getInstanceId(), 
                   request.getCommandTypesCount(),
                   request.getQueryTypesCount(), 
                   request.getEventTypesCount());
        
        try {
            int commandsRegistered = 0;
            int queriesRegistered = 0;
            int eventsRegistered = 0;

            // Create service instance for service discovery
            ServiceInstance serviceInstance = ServiceInstance.newBuilder()
                    .setInstanceId(request.getInstanceId())
                    .setServiceName(request.getServiceName())
                    .setHost(request.getHost())
                    .setPort(request.getPort())
                    .setStatus(HealthStatus.HEALTHY)
                    .addAllCommandTypes(request.getCommandTypesList())
                    .addAllQueryTypes(request.getQueryTypesList())
                    .addAllEventTypes(request.getEventTypesList())
                    .putAllMetadata(request.getMetadataMap())
                    .setLastHeartbeat(Instant.now().toEpochMilli())
                    .setVersion("1.0.0")
                    .build();

            Map<String, String> schemaMap = request.getSchemaMap();

            // Register with service discovery
            serviceDiscoveryService.registerService(serviceInstance);


            pulsarProducerFactory.createProducers(request.getEventTypesList());

            // Register command handlers
            for (String commandType : request.getCommandTypesList()) {
                commandRoutingService.registerCommandHandler(request.getInstanceId(), commandType);
                commandsRegistered++;
            }

            // Register query handlers
            for (String queryType : request.getQueryTypesList()) {
                queryRoutingService.registerQueryHandler(request.getInstanceId(), queryType);
                queriesRegistered++;
            }

            // Register event handlers
            for (String eventType : request.getEventTypesList()) {
                eventRoutingService.registerEventHandler(request.getInstanceId(), eventType);
                eventsRegistered++;
            }

            HandlerRegistrationSummary summary = HandlerRegistrationSummary.newBuilder()
                    .setCommandsRegistered(commandsRegistered)
                    .setQueriesRegistered(queriesRegistered)
                    .setEventsRegistered(eventsRegistered)
                    .build();

            RegisterHandlersResponse response = RegisterHandlersResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(String.format("Successfully registered %d commands, %d queries, %d events", 
                               commandsRegistered, queriesRegistered, eventsRegistered))
                    .setRegistrationId(request.getInstanceId())
                    .setSummary(summary)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info("Successfully registered all handlers for instance: {} - Commands: {}, Queries: {}, Events: {}", 
                       request.getInstanceId(), commandsRegistered, queriesRegistered, eventsRegistered);

        } catch (Exception e) {
            logger.error("Failed to register handlers for instance: {}", request.getInstanceId(), e);
            
            RegisterHandlersResponse response = RegisterHandlersResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to register handlers: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * Enhanced unregistration for all handler types.
     */
    @Override
    public void unregisterHandlers(UnregisterHandlersRequest request, StreamObserver<UnregisterHandlersResponse> responseObserver) {
        logger.info("Unregistering handlers for instance: {} with {} commands, {} queries, {} events", 
                   request.getInstanceId(),
                   request.getCommandTypesCount(),
                   request.getQueryTypesCount(),
                   request.getEventTypesCount());
        
        try {
            int commandsUnregistered = 0;
            int queriesUnregistered = 0;
            int eventsUnregistered = 0;

            // Unregister command handlers
            for (String commandType : request.getCommandTypesList()) {
                commandRoutingService.unregisterCommandHandler(request.getInstanceId(), commandType);
                commandsUnregistered++;
            }

            // Unregister query handlers
            for (String queryType : request.getQueryTypesList()) {
                queryRoutingService.unregisterQueryHandler(request.getInstanceId(), queryType);
                queriesUnregistered++;
            }

            // Unregister event handlers
            for (String eventType : request.getEventTypesList()) {
                eventRoutingService.unregisterEventHandler(request.getInstanceId(), eventType);
                eventsUnregistered++;
            }

            // Unregister from service discovery
            serviceDiscoveryService.unregisterService(request.getInstanceId());

            HandlerRegistrationSummary summary = HandlerRegistrationSummary.newBuilder()
                    .setCommandsUnregistered(commandsUnregistered)
                    .setQueriesUnregistered(queriesUnregistered)
                    .setEventsUnregistered(eventsUnregistered)
                    .build();

            UnregisterHandlersResponse response = UnregisterHandlersResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(String.format("Successfully unregistered %d commands, %d queries, %d events", 
                               commandsUnregistered, queriesUnregistered, eventsUnregistered))
                    .setSummary(summary)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info("Successfully unregistered handlers for instance: {} - Commands: {}, Queries: {}, Events: {}", 
                       request.getInstanceId(), commandsUnregistered, queriesUnregistered, eventsUnregistered);

        } catch (Exception e) {
            logger.error("Failed to unregister handlers for instance: {}", request.getInstanceId(), e);
            
            UnregisterHandlersResponse response = UnregisterHandlersResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to unregister handlers: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * New query discovery method.
     */
    @Override
    public void discoverQueryHandlers(DiscoverQueryHandlersRequest request, StreamObserver<DiscoverQueryHandlersResponse> responseObserver) {
        logger.debug("Discovering query handlers for type: {}", request.getQueryType());
        
        try {
            String targetInstance = queryRoutingService.routeQuery(request.getQueryType());
            List<ServiceInstance> instances;
            
            if (targetInstance != null) {
                // Get the service instance details
                ServiceInstance instance = serviceDiscoveryService.getServiceInstance(targetInstance);
                if (instance != null && instance.getQueryTypesList().contains(request.getQueryType())) {
                    instances = List.of(instance);
                } else {
                    instances = List.of();
                }
            } else {
                instances = List.of();
            }

            int healthyCount = request.getOnlyHealthy() ? instances.size() : instances.size();

            DiscoverQueryHandlersResponse response = DiscoverQueryHandlersResponse.newBuilder()
                    .addAllInstances(instances)
                    .setTotalCount(instances.size())
                    .setHealthyCount(healthyCount)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to discover query handlers for type: {}", request.getQueryType(), e);
            responseObserver.onError(e);
        }
    }

    /**
     * New event discovery method.
     */
    @Override
    public void discoverEventHandlers(DiscoverEventHandlersRequest request, StreamObserver<DiscoverEventHandlersResponse> responseObserver) {
        logger.debug("Discovering event handlers for type: {}", request.getEventType());
        
        try {
            List<String> handlerInstanceIds = eventRoutingService.getAllHandlersForEvent(request.getEventType());
            
            List<ServiceInstance> instances = handlerInstanceIds.stream()
                    .map(serviceDiscoveryService::getServiceInstance)
                    .filter(instance -> instance != null && instance.getEventTypesList().contains(request.getEventType()))
                    .toList();

            int healthyCount = request.getOnlyHealthy() ? 
                    (int) instances.stream().filter(i -> i.getStatus() == HealthStatus.HEALTHY).count() : 
                    instances.size();

            DiscoverEventHandlersResponse response = DiscoverEventHandlersResponse.newBuilder()
                    .addAllInstances(instances)
                    .setTotalCount(instances.size())
                    .setHealthyCount(healthyCount)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to discover event handlers for type: {}", request.getEventType(), e);
            responseObserver.onError(e);
        }
    }

    /**
     * New query submission method.
     */
    @Override
    public void submitQuery(SubmitQueryRequest request, StreamObserver<SubmitQueryResponse> responseObserver) {
        logger.info("Submitting query {} of type: {}", request.getQueryId(), request.getQueryType());
        
        long startTime = System.currentTimeMillis();
        
        try {
            String targetInstance = queryRoutingService.routeQuery(request.getQueryType());
            
            if (targetInstance == null) {
                throw new RuntimeException("No healthy instances available for query type: " + request.getQueryType());
            }

            // TODO: Implement actual query forwarding to target instance
            // For now, return a success response
            long executionTime = System.currentTimeMillis() - startTime;

            SubmitQueryResponse response = SubmitQueryResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Query submitted successfully")
                    .setTargetInstance(targetInstance)
                    .setExecutionTimeMs(executionTime)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info("Successfully submitted query {} to instance {}", request.getQueryId(), targetInstance);

        } catch (Exception e) {
            logger.error("Failed to submit query {}: {}", request.getQueryId(), e.getMessage(), e);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            SubmitQueryResponse response = SubmitQueryResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to submit query: " + e.getMessage())
                    .setErrorCode("QUERY_ROUTING_FAILED")
                    .setExecutionTimeMs(executionTime)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
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
    private final Map<String, ManagedChannel> channelPool = new ConcurrentHashMap<>();
    @Override
    public void submitCommand(SubmitCommandRequest request, StreamObserver<SubmitCommandResponse> responseObserver) {
        logger.debug("📨 Submitting command: {} for aggregate: {}",
            request.getCommandType(), request.getAggregateId());

        try {
            // 1️⃣ Find target instance dynamically
            ServiceInstance targetInstance =
                commandRoutingService.routeCommand(request.getCommandType(), request.getAggregateId());
            String key = "localhost" + ":" + 8083;

            // 2️⃣ Get or create channel from pool
            ManagedChannel channel = channelPool.computeIfAbsent(key, k -> {
                logger.info("🔌 Creating new channel to {}", k);
                return ManagedChannelBuilder.forAddress("localhost", 8083)
                    .usePlaintext() // ❗ TLS can be added later
                    .build();
            });

            // 3️⃣ Get stub
            CommandHandlingServiceGrpc.CommandHandlingServiceBlockingStub stub =
                CommandHandlingServiceGrpc.newBlockingStub(channel);

            // 4️⃣ Forward request
            SubmitCommandResponse targetResponse = stub.submitCommand(request);

            // 5️⃣ Send response back to caller
            responseObserver.onNext(targetResponse);
            responseObserver.onCompleted();

            logger.debug("✅ Routed command {} to {}", request.getCommandId(), key);

        } catch (Exception sre) {
            logger.error("❌ gRPC call failed for command {}: {}", request.getCommandId(), sre.getMessage(), sre);

            responseObserver.onNext(
                SubmitCommandResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("gRPC error: " + sre.getMessage())
                    .setErrorCode("GRPC_CALL_ERROR")
                    .build()
            );
            responseObserver.onCompleted();

        }
    }

    /**
     * 🔄 Cleanup method (e.g., on shutdown or when instances are deregistered)
     */
    public void closeChannel(String host, int port) {
        String key = host + ":" + port;
        ManagedChannel channel = channelPool.remove(key);
        if (channel != null) {
            logger.info("🛑 Shutting down channel to {}", key);
            channel.shutdown();
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

//    @Override
//    public void executeCommand(ExecuteCommandRequest request, StreamObserver<ExecuteCommandResponse> responseObserver) {
//        try {
//            logger.info("Received command execution request for command type: {}", request.getCommandType());
//
//            // Get healthy instances that can handle this command type
//            List<ServiceInstance> healthyInstances = serviceDiscoveryService.getHealthyServices(request.getCommandType());
//
//            if (healthyInstances.isEmpty()) {
//                logger.warn("No healthy instances found for command type: {}", request.getCommandType());
//                responseObserver.onError(Status.UNAVAILABLE
//                    .withDescription("No healthy instances available for command type: " + request.getCommandType())
//                    .asRuntimeException());
//                return;
//            }
//
//            // ...existing code...
//        } catch (Exception e) {
//            logger.error("Error executing command", e);
//            responseObserver.onError(Status.INTERNAL
//                .withDescription("Internal server error: " + e.getMessage())
//                .asRuntimeException());
//        }
//    }
//
//    @Override
//    public void queryHandlers(QueryHandlersRequest request, StreamObserver<QueryHandlersResponse> responseObserver) {
//        try {
//            logger.info("Received query handlers request");
//
//            // Get all healthy command handler services
//            List<ServiceInstance> healthyInstances = serviceDiscoveryService.getHealthyServices(request.getQueryType());
//
//            // ...existing code...
//        } catch (Exception e) {
//            logger.error("Error querying handlers", e);
//            responseObserver.onError(Status.INTERNAL
//                .withDescription("Internal server error: " + e.getMessage())
//                .asRuntimeException());
//        }
//    }
}