package com.example.customaxonserver.grpc.service;

import com.example.customaxonserver.service.*;
import com.example.customaxonserver.util.pulser.PulsarProducerFactory;
import com.example.grpc.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
public class EventHandlingServiceImpl extends EventHandlingServiceGrpc.EventHandlingServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlingServiceImpl.class);

    private final CommandRoutingService commandRoutingService;
    private final QueryRoutingService queryRoutingService;
    private final EventRoutingService eventRoutingService;
    private final ServiceDiscoveryService serviceDiscoveryService;
    private final StreamingHeartbeatService streamingHeartbeatService;
    private final ObjectMapper objectMapper;
    private final PulsarProducerFactory producerFactory;

    // Store active health streams for real-time updates
    private final ConcurrentMap<String, StreamObserver<HealthStreamResponse>> healthStreams = new ConcurrentHashMap<>();

    @Autowired
    public EventHandlingServiceImpl(CommandRoutingService commandRoutingService,
                                    QueryRoutingService queryRoutingService,
                                    EventRoutingService eventRoutingService,
                                    ServiceDiscoveryService serviceDiscoveryService,
                                    StreamingHeartbeatService streamingHeartbeatService,
                                    ObjectMapper objectMapper, PulsarProducerFactory producerFactory) {
        this.commandRoutingService = commandRoutingService;
        this.queryRoutingService = queryRoutingService;
        this.eventRoutingService = eventRoutingService;
        this.serviceDiscoveryService = serviceDiscoveryService;
        this.streamingHeartbeatService = streamingHeartbeatService;
        this.objectMapper = objectMapper;
      this.producerFactory = producerFactory;
    }


    private final Map<String, ManagedChannel> channelPool = new ConcurrentHashMap<>();
    @Override
    public void submitEvent(SubmitEventRequest request, StreamObserver<SubmitEventResponse> responseObserver) {
        logger.debug("📨 Submitting command: {} for aggregate: {}",
            request.getEventType(), request.getAggregateId());

        try {
            String simpleName = request.getEventType().substring(request.getEventType().lastIndexOf('.') + 1);

            producerFactory.sendMessage(request.getEventType(), request);
            // 2️⃣ Get or create channel from pool
            responseObserver.onNext(
                SubmitEventResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("gRPC ")
                    .setErrorCode("GRPC_CALL_Success")
                    .build()
            );
            responseObserver.onCompleted();

   //         logger.debug("✅ Routed command {} to {}", request.getEventType(), key);

        } catch (Exception sre) {
            logger.error(" gRPC call failed for command {}: {}", request.getEventType(), sre.getMessage(), sre);

            responseObserver.onNext(
                SubmitEventResponse.newBuilder()
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