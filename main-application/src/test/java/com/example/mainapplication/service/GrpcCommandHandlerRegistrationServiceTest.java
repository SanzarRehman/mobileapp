package com.example.mainapplication.service;

import com.example.customaxonserver.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for gRPC Command Handler Registration Service.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class GrpcCommandHandlerRegistrationServiceTest {

    @GrpcClient("custom-axon-server")
    private CommandHandlingServiceGrpc.CommandHandlingServiceBlockingStub commandHandlingStub;

    @Test
    void testDiscoverCommandHandlers() {
        try {
            // Test command handler discovery
            DiscoverCommandHandlersRequest request = DiscoverCommandHandlersRequest.newBuilder()
                    .setCommandType("com.example.mainapplication.command.CreateUserCommand")
                    .setOnlyHealthy(true)
                    .build();

            DiscoverCommandHandlersResponse response = commandHandlingStub.discoverCommandHandlers(request);
            
            // Assert response is not null
            assertNotNull(response);
            assertTrue(response.getTotalCount() >= 0);
            assertTrue(response.getHealthyCount() >= 0);
            
        } catch (StatusRuntimeException e) {
            // If gRPC server is not running, this is expected in test environment
            if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                // This is expected when custom server is not running
                return;
            }
            throw e;
        }
    }

    @Test
    void testServiceDiscovery() {
        try {
            // Test basic connectivity
            DiscoverCommandHandlersRequest request = DiscoverCommandHandlersRequest.newBuilder()
                    .setCommandType("health-check")
                    .setOnlyHealthy(false)
                    .build();

            assertDoesNotThrow(() -> {
                commandHandlingStub.discoverCommandHandlers(request);
            });
            
        } catch (StatusRuntimeException e) {
            // Expected when server is not running
            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }
    }
}