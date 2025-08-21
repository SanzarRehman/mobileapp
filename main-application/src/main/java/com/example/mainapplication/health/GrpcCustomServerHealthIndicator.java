package com.example.mainapplication.health;

import com.example.grpc.common.*;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator that checks connectivity to the Custom Axon Server via gRPC.
 */
@Component
public class GrpcCustomServerHealthIndicator implements HealthIndicator {

    @GrpcClient("custom-axon-server")
    private CommandHandlingServiceGrpc.CommandHandlingServiceBlockingStub commandHandlingStub;

    @Override
    public Health health() {
        try {
            // Try to discover command handlers as a health check
            DiscoverCommandHandlersRequest request = DiscoverCommandHandlersRequest.newBuilder()
                    .setCommandType("health-check")
                    .setOnlyHealthy(false)
                    .build();

            DiscoverCommandHandlersResponse response = commandHandlingStub.discoverCommandHandlers(request);
            
            return Health.up()
                    .withDetail("grpcCustomServer", "Available")
                    .withDetail("totalInstances", response.getTotalCount())
                    .withDetail("healthyInstances", response.getHealthyCount())
                    .withDetail("protocol", "gRPC")
                    .build();
                    
        } catch (StatusRuntimeException e) {
            return Health.down()
                    .withDetail("grpcCustomServer", "Unavailable")
                    .withDetail("protocol", "gRPC")
                    .withDetail("error", e.getStatus().toString())
                    .withDetail("description", e.getStatus().getDescription())
                    .withException(e)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("grpcCustomServer", "Unavailable")
                    .withDetail("protocol", "gRPC")
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}
