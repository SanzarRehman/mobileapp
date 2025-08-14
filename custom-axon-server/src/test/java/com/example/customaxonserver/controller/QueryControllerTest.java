package com.example.customaxonserver.controller;

import com.example.customaxonserver.model.QueryMessage;
import com.example.customaxonserver.model.QueryResponse;
import com.example.customaxonserver.service.QueryRoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueryRoutingService queryRoutingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitQuery_WithValidQuery_ShouldReturnRoutedResponse() throws Exception {
        // Given
        QueryMessage queryMessage = new QueryMessage(
                "query-123",
                "FindUserQuery",
                Map.of("userId", "user-1"),
                Map.of("correlationId", "corr-123"),
                "UserProjection"
        );
        
        when(queryRoutingService.routeQuery("FindUserQuery")).thenReturn("instance-1");

        // When & Then
        mockMvc.perform(post("/api/queries/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(queryMessage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value("query-123"))
                .andExpect(jsonPath("$.status").value("ROUTED"))
                .andExpect(jsonPath("$.targetInstance").value("instance-1"));

        verify(queryRoutingService).routeQuery("FindUserQuery");
    }

    @Test
    void submitQuery_WithRoutingException_ShouldReturnServiceUnavailable() throws Exception {
        // Given
        QueryMessage queryMessage = new QueryMessage(
                "query-123",
                "FindUserQuery",
                Map.of("userId", "user-1"),
                null,
                "UserProjection"
        );
        
        when(queryRoutingService.routeQuery("FindUserQuery"))
                .thenThrow(new QueryRoutingService.QueryRoutingException("No healthy instances"));

        // When & Then
        mockMvc.perform(post("/api/queries/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(queryMessage)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.queryId").value("query-123"))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("No healthy instances"));
    }

    @Test
    void submitQuery_WithUnexpectedException_ShouldReturnInternalServerError() throws Exception {
        // Given
        QueryMessage queryMessage = new QueryMessage(
                "query-123",
                "FindUserQuery",
                Map.of("userId", "user-1"),
                null,
                "UserProjection"
        );
        
        when(queryRoutingService.routeQuery("FindUserQuery"))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        mockMvc.perform(post("/api/queries/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(queryMessage)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.queryId").value("query-123"))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("Internal server error: Unexpected error"));
    }

    @Test
    void submitQuery_WithInvalidQuery_ShouldReturnBadRequest() throws Exception {
        // Given - Invalid query with missing required fields
        QueryMessage invalidQuery = new QueryMessage(
                null,
                "", // Empty query type
                null,
                null,
                null
        );

        // When & Then
        mockMvc.perform(post("/api/queries/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidQuery)))
                .andExpect(status().isBadRequest());

        verify(queryRoutingService, never()).routeQuery(any());
    }

    @Test
    void registerHandler_ShouldReturnSuccess() throws Exception {
        // Given
        String instanceId = "instance-1";
        String queryType = "FindUserQuery";

        // When & Then
        mockMvc.perform(post("/api/queries/handlers/{instanceId}/{queryType}", instanceId, queryType))
                .andExpect(status().isOk())
                .andExpect(content().string("Query handler registered successfully"));

        verify(queryRoutingService).registerQueryHandler(instanceId, queryType);
    }

    @Test
    void registerHandler_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String instanceId = "instance-1";
        String queryType = "FindUserQuery";
        
        doThrow(new RuntimeException("Registration failed"))
                .when(queryRoutingService).registerQueryHandler(instanceId, queryType);

        // When & Then
        mockMvc.perform(post("/api/queries/handlers/{instanceId}/{queryType}", instanceId, queryType))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to register query handler: Registration failed"));
    }

    @Test
    void unregisterHandler_ShouldReturnSuccess() throws Exception {
        // Given
        String instanceId = "instance-1";
        String queryType = "FindUserQuery";

        // When & Then
        mockMvc.perform(delete("/api/queries/handlers/{instanceId}/{queryType}", instanceId, queryType))
                .andExpect(status().isOk())
                .andExpect(content().string("Query handler unregistered successfully"));

        verify(queryRoutingService).unregisterQueryHandler(instanceId, queryType);
    }

    @Test
    void unregisterHandler_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String instanceId = "instance-1";
        String queryType = "FindUserQuery";
        
        doThrow(new RuntimeException("Unregistration failed"))
                .when(queryRoutingService).unregisterQueryHandler(instanceId, queryType);

        // When & Then
        mockMvc.perform(delete("/api/queries/handlers/{instanceId}/{queryType}", instanceId, queryType))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to unregister query handler: Unregistration failed"));
    }

    @Test
    void updateInstanceHealth_ShouldReturnSuccess() throws Exception {
        // Given
        String instanceId = "instance-1";
        String status = "healthy";

        // When & Then
        mockMvc.perform(post("/api/queries/instances/{instanceId}/health", instanceId)
                .param("status", status))
                .andExpect(status().isOk())
                .andExpect(content().string("Health status updated successfully"));

        verify(queryRoutingService).updateInstanceHealth(instanceId, status);
    }

    @Test
    void updateInstanceHealth_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String instanceId = "instance-1";
        String status = "healthy";
        
        doThrow(new RuntimeException("Health update failed"))
                .when(queryRoutingService).updateInstanceHealth(instanceId, status);

        // When & Then
        mockMvc.perform(post("/api/queries/instances/{instanceId}/health", instanceId)
                .param("status", status))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to update health status: Health update failed"));
    }

    @Test
    void getQueryTypesForInstance_ShouldReturnQueryTypes() throws Exception {
        // Given
        String instanceId = "instance-1";
        Set<String> queryTypes = Set.of("FindUserQuery", "FindOrderQuery");
        
        when(queryRoutingService.getQueryTypesForInstance(instanceId)).thenReturn(queryTypes);

        // When & Then
        mockMvc.perform(get("/api/queries/instances/{instanceId}/queries", instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        verify(queryRoutingService).getQueryTypesForInstance(instanceId);
    }

    @Test
    void getQueryTypesForInstance_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String instanceId = "instance-1";
        
        when(queryRoutingService.getQueryTypesForInstance(instanceId))
                .thenThrow(new RuntimeException("Failed to get query types"));

        // When & Then
        mockMvc.perform(get("/api/queries/instances/{instanceId}/queries", instanceId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getInstancesForQueryType_ShouldReturnInstances() throws Exception {
        // Given
        String queryType = "FindUserQuery";
        List<String> instances = List.of("instance-1", "instance-2");
        
        when(queryRoutingService.getInstancesForQueryType(queryType)).thenReturn(instances);

        // When & Then
        mockMvc.perform(get("/api/queries/types/{queryType}/instances", queryType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("instance-1"))
                .andExpect(jsonPath("$[1]").value("instance-2"));

        verify(queryRoutingService).getInstancesForQueryType(queryType);
    }

    @Test
    void getInstancesForQueryType_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String queryType = "FindUserQuery";
        
        when(queryRoutingService.getInstancesForQueryType(queryType))
                .thenThrow(new RuntimeException("Failed to get instances"));

        // When & Then
        mockMvc.perform(get("/api/queries/types/{queryType}/instances", queryType))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getAllInstancesHealth_ShouldReturnHealthMap() throws Exception {
        // Given
        Map<String, String> healthMap = Map.of(
                "instance-1", "healthy",
                "instance-2", "unhealthy"
        );
        
        when(queryRoutingService.getAllInstancesHealth()).thenReturn(healthMap);

        // When & Then
        mockMvc.perform(get("/api/queries/instances/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['instance-1']").value("healthy"))
                .andExpect(jsonPath("$.['instance-2']").value("unhealthy"));

        verify(queryRoutingService).getAllInstancesHealth();
    }

    @Test
    void getAllInstancesHealth_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(queryRoutingService.getAllInstancesHealth())
                .thenThrow(new RuntimeException("Failed to get health info"));

        // When & Then
        mockMvc.perform(get("/api/queries/instances/health"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void removeInstance_ShouldReturnSuccess() throws Exception {
        // Given
        String instanceId = "instance-1";

        // When & Then
        mockMvc.perform(delete("/api/queries/instances/{instanceId}", instanceId))
                .andExpect(status().isOk())
                .andExpect(content().string("Query instance removed successfully"));

        verify(queryRoutingService).removeInstance(instanceId);
    }

    @Test
    void removeInstance_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String instanceId = "instance-1";
        
        doThrow(new RuntimeException("Failed to remove instance"))
                .when(queryRoutingService).removeInstance(instanceId);

        // When & Then
        mockMvc.perform(delete("/api/queries/instances/{instanceId}", instanceId))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to remove query instance: Failed to remove instance"));
    }
}