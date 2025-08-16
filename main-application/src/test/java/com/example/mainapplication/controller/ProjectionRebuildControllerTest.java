package com.example.mainapplication.controller;

import com.example.mainapplication.service.ProjectionRebuildService;
import com.example.mainapplication.service.ProjectionRebuildService.RebuildStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectionRebuildController.class)
class ProjectionRebuildControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectionRebuildService projectionRebuildService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testRebuildAllProjections_Success() throws Exception {
        // Arrange
        String rebuildId = "user-projections-123456789";
        when(projectionRebuildService.rebuildAllUserProjections())
            .thenReturn(CompletableFuture.completedFuture(rebuildId));

        // Act & Assert
        mockMvc.perform(post("/api/projections/rebuild/all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rebuildId").value(rebuildId))
                .andExpect(jsonPath("$.message").value("Rebuild of all user projections started successfully"));
    }

    @Test
    void testRebuildProjectionsForAggregate_Success() throws Exception {
        // Arrange
        String aggregateId = "user-1";
        String rebuildId = "aggregate-user-1-123456789";
        when(projectionRebuildService.rebuildProjectionsForAggregate(aggregateId))
            .thenReturn(CompletableFuture.completedFuture(rebuildId));

        // Act & Assert
        mockMvc.perform(post("/api/projections/rebuild/aggregate/{aggregateId}", aggregateId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rebuildId").value(rebuildId))
                .andExpect(jsonPath("$.message").value("Rebuild of projections for aggregate user-1 started successfully"));
    }

    @Test
    void testRebuildProjectionsForAggregate_EmptyAggregateId() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/projections/rebuild/aggregate/{aggregateId}", "")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rebuildId").isEmpty())
                .andExpect(jsonPath("$.message").value("Aggregate ID cannot be empty"));
    }

    @Test
    void testGetRebuildStatus_Success() throws Exception {
        // Arrange
        String rebuildId = "test-rebuild-123";
        RebuildStatus status = new RebuildStatus(rebuildId, "user-projections", RebuildStatus.Status.IN_PROGRESS);
        status.setTotalEvents(100);
        status.setProcessedEvents(50);
        status.setPhase("REPLAYING_EVENTS");

        when(projectionRebuildService.getRebuildStatus(rebuildId)).thenReturn(status);

        // Act & Assert
        mockMvc.perform(get("/api/projections/rebuild/status/{rebuildId}", rebuildId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebuildId").value(rebuildId))
                .andExpect(jsonPath("$.projectionType").value("user-projections"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.phase").value("REPLAYING_EVENTS"))
                .andExpect(jsonPath("$.totalEvents").value(100))
                .andExpect(jsonPath("$.processedEvents").value(50))
                .andExpect(jsonPath("$.progressPercentage").value(50.0));
    }

    @Test
    void testGetRebuildStatus_NotFound() throws Exception {
        // Arrange
        String rebuildId = "non-existent-rebuild";
        when(projectionRebuildService.getRebuildStatus(rebuildId)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/projections/rebuild/status/{rebuildId}", rebuildId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAllRebuildStatuses_Success() throws Exception {
        // Arrange
        String rebuildId1 = "rebuild-1";
        String rebuildId2 = "rebuild-2";
        
        RebuildStatus status1 = new RebuildStatus(rebuildId1, "user-projections", RebuildStatus.Status.COMPLETED);
        RebuildStatus status2 = new RebuildStatus(rebuildId2, "aggregate-user-1", RebuildStatus.Status.IN_PROGRESS);
        
        Map<String, RebuildStatus> statuses = Map.of(
            rebuildId1, status1,
            rebuildId2, status2
        );

        when(projectionRebuildService.getAllRebuildStatuses()).thenReturn(statuses);

        // Act & Assert
        mockMvc.perform(get("/api/projections/rebuild/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$['" + rebuildId1 + "'].status").value("COMPLETED"))
                .andExpect(jsonPath("$['" + rebuildId2 + "'].status").value("IN_PROGRESS"));
    }

    @Test
    void testCancelRebuild_Success() throws Exception {
        // Arrange
        String rebuildId = "test-rebuild-123";
        when(projectionRebuildService.cancelRebuild(rebuildId)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/projections/rebuild/cancel/{rebuildId}", rebuildId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebuildId").value(rebuildId))
                .andExpect(jsonPath("$.message").value("Rebuild cancelled successfully"));
    }

    @Test
    void testCancelRebuild_NotFound() throws Exception {
        // Arrange
        String rebuildId = "non-existent-rebuild";
        when(projectionRebuildService.cancelRebuild(rebuildId)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/projections/rebuild/cancel/{rebuildId}", rebuildId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testRebuildAllProjections_ServiceException() throws Exception {
        // Arrange
        when(projectionRebuildService.rebuildAllUserProjections())
            .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        mockMvc.perform(post("/api/projections/rebuild/all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.rebuildId").isEmpty())
                .andExpect(jsonPath("$.message").value("Failed to start rebuild: Service error"));
    }

    @Test
    void testRebuildProjectionsForAggregate_ServiceException() throws Exception {
        // Arrange
        String aggregateId = "user-1";
        when(projectionRebuildService.rebuildProjectionsForAggregate(anyString()))
            .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        mockMvc.perform(post("/api/projections/rebuild/aggregate/{aggregateId}", aggregateId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.rebuildId").isEmpty())
                .andExpect(jsonPath("$.message").value("Failed to start rebuild: Service error"));
    }
}