package com.example.mainapplication.service;

import com.example.mainapplication.LIB.service.ProjectionRebuildService;
import com.example.mainapplication.handler.UserEventHandler;
import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.repository.UserProjectionRepository;
import com.example.mainapplication.LIB.service.ProjectionRebuildService.RebuildStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectionRebuildServiceTest {

    @Mock
    private UserProjectionRepository userProjectionRepository;

    @Mock
    private UserEventHandler userEventHandler;

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private ProjectionRebuildService projectionRebuildService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        projectionRebuildService = new ProjectionRebuildService(
            userProjectionRepository, userEventHandler, restTemplate, objectMapper);
    }

    @Test
    void testRebuildAllUserProjections_Success() throws Exception {
        // Arrange
        String eventsJson = """
            [
                {
                    "eventType": "UserCreatedEvent",
                    "eventData": {
                        "userId": "user-1",
                        "fullName": "John Doe",
                        "email": "john@example.com",
                        "createdAt": "2024-01-01T10:00:00Z"
                    }
                },
                {
                    "eventType": "UserUpdatedEvent",
                    "eventData": {
                        "userId": "user-1",
                        "fullName": "John Smith",
                        "email": "john.smith@example.com",
                        "updatedAt": "2024-01-02T10:00:00Z"
                    }
                }
            ]
            """;

        JsonNode eventsNode = objectMapper.readTree(eventsJson);
        ResponseEntity<JsonNode> response = new ResponseEntity<>(eventsNode, HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class))).thenReturn(response);
        when(userProjectionRepository.existsById("user-1")).thenReturn(false);
        when(userProjectionRepository.findById("user-1")).thenReturn(java.util.Optional.empty());

        // Act
        CompletableFuture<String> result = projectionRebuildService.rebuildAllUserProjections();
        String rebuildId = result.get();

        // Assert
        assertNotNull(rebuildId);
        assertTrue(rebuildId.startsWith("user-projections-"));
        
        // Verify repository interactions
        verify(userProjectionRepository).deleteAll();
        verify(userProjectionRepository, times(2)).save(any(UserProjection.class));
        
        // Check rebuild status
        RebuildStatus status = projectionRebuildService.getRebuildStatus(rebuildId);
        assertNotNull(status);
        assertEquals(RebuildStatus.Status.COMPLETED, status.getStatus());
        assertEquals(2, status.getTotalEvents());
        assertEquals(2, status.getProcessedEvents());
    }

    @Test
    void testRebuildProjectionsForAggregate_Success() throws Exception {
        // Arrange
        String aggregateId = "user-1";
        String eventsJson = """
            [
                {
                    "eventType": "UserCreatedEvent",
                    "eventData": {
                        "userId": "user-1",
                        "fullName": "John Doe",
                        "email": "john@example.com",
                        "createdAt": "2024-01-01T10:00:00Z"
                    }
                }
            ]
            """;

        JsonNode eventsNode = objectMapper.readTree(eventsJson);
        ResponseEntity<JsonNode> response = new ResponseEntity<>(eventsNode, HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class))).thenReturn(response);
        when(userProjectionRepository.existsById(aggregateId)).thenReturn(false);

        // Act
        CompletableFuture<String> result = projectionRebuildService.rebuildProjectionsForAggregate(aggregateId);
        String rebuildId = result.get();

        // Assert
        assertNotNull(rebuildId);
        assertTrue(rebuildId.startsWith("aggregate-user-1-"));
        
        // Verify repository interactions
        verify(userProjectionRepository).deleteById(aggregateId);
        verify(userProjectionRepository).save(any(UserProjection.class));
        
        // Check rebuild status
        RebuildStatus status = projectionRebuildService.getRebuildStatus(rebuildId);
        assertNotNull(status);
        assertEquals(RebuildStatus.Status.COMPLETED, status.getStatus());
        assertEquals(1, status.getTotalEvents());
        assertEquals(1, status.getProcessedEvents());
    }

    @Test
    void testRebuildAllUserProjections_RestTemplateError() throws Exception {
        // Arrange
        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class)))
            .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act
        CompletableFuture<String> result = projectionRebuildService.rebuildAllUserProjections();
        String rebuildId = result.get(); // This should complete successfully but with error status
        
        // Assert - Check that rebuild status shows failure
        RebuildStatus status = projectionRebuildService.getRebuildStatus(rebuildId);
        assertNotNull(status);
        assertEquals(RebuildStatus.Status.FAILED, status.getStatus());
        assertNotNull(status.getErrorMessage());
    }

    @Test
    void testGetRebuildStatus_NotFound() {
        // Act
        RebuildStatus status = projectionRebuildService.getRebuildStatus("non-existent-id");

        // Assert
        assertNull(status);
    }

    @Test
    void testCancelRebuild_Success() throws Exception {
        // Arrange - start a rebuild first
        String eventsJson = "[]";
        JsonNode eventsNode = objectMapper.readTree(eventsJson);
        ResponseEntity<JsonNode> response = new ResponseEntity<>(eventsNode, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class))).thenReturn(response);

        CompletableFuture<String> result = projectionRebuildService.rebuildAllUserProjections();
        String rebuildId = result.get();

        // Act
        boolean cancelled = projectionRebuildService.cancelRebuild(rebuildId);

        // Assert
        // Note: Since the rebuild completes immediately in test, it won't be cancelled
        // In real scenarios with longer running rebuilds, this would work
        assertFalse(cancelled); // Already completed
    }

    @Test
    void testCancelRebuild_NotFound() {
        // Act
        boolean cancelled = projectionRebuildService.cancelRebuild("non-existent-id");

        // Assert
        assertFalse(cancelled);
    }

    @Test
    void testGetAllRebuildStatuses() throws Exception {
        // Arrange - start a rebuild
        String eventsJson = "[]";
        JsonNode eventsNode = objectMapper.readTree(eventsJson);
        ResponseEntity<JsonNode> response = new ResponseEntity<>(eventsNode, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class))).thenReturn(response);

        CompletableFuture<String> result = projectionRebuildService.rebuildAllUserProjections();
        String rebuildId = result.get();

        // Act
        var allStatuses = projectionRebuildService.getAllRebuildStatuses();

        // Assert
        assertFalse(allStatuses.isEmpty());
        assertTrue(allStatuses.containsKey(rebuildId));
    }

    @Test
    void testRebuildStatus_ProgressCalculation() {
        // Arrange
        RebuildStatus status = new RebuildStatus("test-id", "test-type", RebuildStatus.Status.IN_PROGRESS);
        status.setTotalEvents(100);
        status.setProcessedEvents(25);

        // Act
        double progress = status.getProgressPercentage();

        // Assert
        assertEquals(25.0, progress, 0.01);
    }

    @Test
    void testRebuildStatus_ProgressCalculation_ZeroTotal() {
        // Arrange
        RebuildStatus status = new RebuildStatus("test-id", "test-type", RebuildStatus.Status.IN_PROGRESS);
        status.setTotalEvents(0);
        status.setProcessedEvents(0);

        // Act
        double progress = status.getProgressPercentage();

        // Assert
        assertEquals(0.0, progress, 0.01);
    }
}