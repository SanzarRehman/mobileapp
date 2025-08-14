package com.example.customaxonserver.controller;

import com.example.customaxonserver.model.CommandMessage;
import com.example.customaxonserver.service.CommandRoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommandController.class)
class CommandControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private CommandRoutingService commandRoutingService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testSubmitCommand_WithValidCommand_ShouldReturnRoutedResponse() throws Exception {
        // Given
        CommandMessage commandMessage = new CommandMessage(
                "cmd-123",
                "CreateUserCommand",
                "user-123",
                Map.of("name", "John Doe", "email", "john@example.com"),
                Map.of("correlationId", "corr-123")
        );
        
        when(commandRoutingService.routeCommand("CreateUserCommand", "user-123"))
                .thenReturn("instance-1");
        
        // When & Then
        mockMvc.perform(post("/api/commands/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(commandMessage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandId").value("cmd-123"))
                .andExpect(jsonPath("$.status").value("ROUTED"))
                .andExpect(jsonPath("$.targetInstance").value("instance-1"));
    }
    
    @Test
    void testSubmitCommand_WithRoutingException_ShouldReturnServiceUnavailable() throws Exception {
        // Given
        CommandMessage commandMessage = new CommandMessage(
                "cmd-123",
                "CreateUserCommand",
                "user-123",
                Map.of("name", "John Doe"),
                null
        );
        
        when(commandRoutingService.routeCommand("CreateUserCommand", "user-123"))
                .thenThrow(new CommandRoutingService.CommandRoutingException("No healthy instances"));
        
        // When & Then
        mockMvc.perform(post("/api/commands/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(commandMessage)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.commandId").value("cmd-123"))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("No healthy instances"));
    }
    
    @Test
    void testSubmitCommand_WithInvalidCommand_ShouldReturnBadRequest() throws Exception {
        // Given - command with missing required fields
        Map<String, Object> invalidCommand = new HashMap<>();
        invalidCommand.put("commandId", "cmd-123");
        // Missing commandType and aggregateId
        
        // When & Then
        mockMvc.perform(post("/api/commands/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidCommand)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void testRegisterHandler_ShouldReturnSuccess() throws Exception {
        // Given
        String instanceId = "instance-1";
        String commandType = "CreateUserCommand";
        
        doNothing().when(commandRoutingService).registerCommandHandler(instanceId, commandType);
        
        // When & Then
        mockMvc.perform(post("/api/commands/handlers/{instanceId}/{commandType}", instanceId, commandType))
                .andExpect(status().isOk())
                .andExpect(content().string("Handler registered successfully"));
        
        verify(commandRoutingService).registerCommandHandler(instanceId, commandType);
    }
    
    @Test
    void testRegisterHandler_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String instanceId = "instance-1";
        String commandType = "CreateUserCommand";
        
        doThrow(new RuntimeException("Redis connection failed"))
                .when(commandRoutingService).registerCommandHandler(instanceId, commandType);
        
        // When & Then
        mockMvc.perform(post("/api/commands/handlers/{instanceId}/{commandType}", instanceId, commandType))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to register handler: Redis connection failed"));
    }
    
    @Test
    void testUnregisterHandler_ShouldReturnSuccess() throws Exception {
        // Given
        String instanceId = "instance-1";
        String commandType = "CreateUserCommand";
        
        doNothing().when(commandRoutingService).unregisterCommandHandler(instanceId, commandType);
        
        // When & Then
        mockMvc.perform(delete("/api/commands/handlers/{instanceId}/{commandType}", instanceId, commandType))
                .andExpect(status().isOk())
                .andExpect(content().string("Handler unregistered successfully"));
        
        verify(commandRoutingService).unregisterCommandHandler(instanceId, commandType);
    }
    
    @Test
    void testUpdateInstanceHealth_ShouldReturnSuccess() throws Exception {
        // Given
        String instanceId = "instance-1";
        String status = "healthy";
        
        doNothing().when(commandRoutingService).updateInstanceHealth(instanceId, status);
        
        // When & Then
        mockMvc.perform(post("/api/commands/instances/{instanceId}/health", instanceId)
                .param("status", status))
                .andExpect(status().isOk())
                .andExpect(content().string("Health status updated successfully"));
        
        verify(commandRoutingService).updateInstanceHealth(instanceId, status);
    }
    
    @Test
    void testGetCommandTypesForInstance_ShouldReturnCommandTypes() throws Exception {
        // Given
        String instanceId = "instance-1";
        Set<String> commandTypes = new HashSet<>(Arrays.asList("CreateUserCommand", "UpdateUserCommand"));
        
        when(commandRoutingService.getCommandTypesForInstance(instanceId)).thenReturn(commandTypes);
        
        // When & Then
        mockMvc.perform(get("/api/commands/instances/{instanceId}/commands", instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
        
        verify(commandRoutingService).getCommandTypesForInstance(instanceId);
    }
    
    @Test
    void testGetInstancesForCommandType_ShouldReturnInstances() throws Exception {
        // Given
        String commandType = "CreateUserCommand";
        List<String> instances = Arrays.asList("instance-1", "instance-2");
        
        when(commandRoutingService.getInstancesForCommandType(commandType)).thenReturn(instances);
        
        // When & Then
        mockMvc.perform(get("/api/commands/types/{commandType}/instances", commandType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("instance-1"))
                .andExpect(jsonPath("$[1]").value("instance-2"));
        
        verify(commandRoutingService).getInstancesForCommandType(commandType);
    }
    
    @Test
    void testRemoveInstance_ShouldReturnSuccess() throws Exception {
        // Given
        String instanceId = "instance-1";
        
        doNothing().when(commandRoutingService).removeInstance(instanceId);
        
        // When & Then
        mockMvc.perform(delete("/api/commands/instances/{instanceId}", instanceId))
                .andExpect(status().isOk())
                .andExpect(content().string("Instance removed successfully"));
        
        verify(commandRoutingService).removeInstance(instanceId);
    }
    
    @Test
    void testRemoveInstance_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String instanceId = "instance-1";
        
        doThrow(new CommandRoutingService.CommandRoutingException("Failed to remove instance"))
                .when(commandRoutingService).removeInstance(instanceId);
        
        // When & Then
        mockMvc.perform(delete("/api/commands/instances/{instanceId}", instanceId))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to remove instance: Failed to remove instance"));
    }
    
    @Test
    void testGetCommandTypesForInstance_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String instanceId = "instance-1";
        
        when(commandRoutingService.getCommandTypesForInstance(instanceId))
                .thenThrow(new RuntimeException("Redis connection failed"));
        
        // When & Then
        mockMvc.perform(get("/api/commands/instances/{instanceId}/commands", instanceId))
                .andExpect(status().isInternalServerError());
        
        verify(commandRoutingService).getCommandTypesForInstance(instanceId);
    }
    
    @Test
    void testGetInstancesForCommandType_WithException_ShouldReturnInternalServerError() throws Exception {
        // Given
        String commandType = "CreateUserCommand";
        
        when(commandRoutingService.getInstancesForCommandType(commandType))
                .thenThrow(new RuntimeException("Redis connection failed"));
        
        // When & Then
        mockMvc.perform(get("/api/commands/types/{commandType}/instances", commandType))
                .andExpect(status().isInternalServerError());
        
        verify(commandRoutingService).getInstancesForCommandType(commandType);
    }
}