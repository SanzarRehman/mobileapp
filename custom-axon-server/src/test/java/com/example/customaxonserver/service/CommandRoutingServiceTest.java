package com.example.customaxonserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CommandRoutingServiceTest {
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private SetOperations<String, Object> setOperations;
    
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    
    private ObjectMapper objectMapper;
    private CommandRoutingService commandRoutingService;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        commandRoutingService = new CommandRoutingService(redisTemplate, objectMapper);
        
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }
    
    @Test
    void testRouteCommand_WithHealthyInstances_ShouldReturnInstance() {
        // Given
        String commandType = "CreateUserCommand";
        String aggregateId = "user-123";
        
        Set<Object> instances = new HashSet<>(Arrays.asList("instance-1", "instance-2"));
        when(setOperations.members("command_routes:" + commandType)).thenReturn(instances);
        
        Map<Object, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "healthy");
        when(hashOperations.entries("instance_health:instance-1")).thenReturn(healthInfo);
        when(hashOperations.entries("instance_health:instance-2")).thenReturn(healthInfo);
        
        // When
        String result = commandRoutingService.routeCommand(commandType, aggregateId);
        
        // Then
        assertNotNull(result);
        assertTrue(Arrays.asList("instance-1", "instance-2").contains(result));
    }
    
    @Test
    void testRouteCommand_WithNoHealthyInstances_ShouldThrowException() {
        // Given
        String commandType = "CreateUserCommand";
        String aggregateId = "user-123";
        
        Set<Object> instances = new HashSet<>(Arrays.asList("instance-1"));
        when(setOperations.members("command_routes:" + commandType)).thenReturn(instances);
        
        Map<Object, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "unhealthy");
        when(hashOperations.entries("instance_health:instance-1")).thenReturn(healthInfo);
        
        // When & Then
        CommandRoutingService.CommandRoutingException exception = assertThrows(
                CommandRoutingService.CommandRoutingException.class,
                () -> commandRoutingService.routeCommand(commandType, aggregateId)
        );
        
        assertEquals("No healthy instances available for command type: " + commandType, exception.getMessage());
    }
    
    @Test
    void testRouteCommand_ConsistentHashing_ShouldReturnSameInstanceForSameAggregate() {
        // Given
        String commandType = "CreateUserCommand";
        String aggregateId = "user-123";
        
        Set<Object> instances = new HashSet<>(Arrays.asList("instance-1", "instance-2"));
        when(setOperations.members("command_routes:" + commandType)).thenReturn(instances);
        
        Map<Object, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "healthy");
        when(hashOperations.entries("instance_health:instance-1")).thenReturn(healthInfo);
        when(hashOperations.entries("instance_health:instance-2")).thenReturn(healthInfo);
        
        // When
        String result1 = commandRoutingService.routeCommand(commandType, aggregateId);
        String result2 = commandRoutingService.routeCommand(commandType, aggregateId);
        
        // Then
        assertEquals(result1, result2, "Same aggregate should always route to same instance");
    }
    
    @Test
    void testRegisterCommandHandler_ShouldAddToRoutingTable() {
        // Given
        String instanceId = "instance-1";
        String commandType = "CreateUserCommand";
        
        // When
        commandRoutingService.registerCommandHandler(instanceId, commandType);
        
        // Then
        verify(setOperations).add("command_routes:" + commandType, instanceId);
        verify(setOperations).add("handler_registry:" + instanceId, commandType);
        verify(hashOperations).putAll(eq("instance_health:" + instanceId), any(Map.class));
        verify(redisTemplate).expire("instance_health:" + instanceId, Duration.ofMinutes(2));
    }
    
    @Test
    void testUnregisterCommandHandler_ShouldRemoveFromRoutingTable() {
        // Given
        String instanceId = "instance-1";
        String commandType = "CreateUserCommand";
        
        // When
        commandRoutingService.unregisterCommandHandler(instanceId, commandType);
        
        // Then
        verify(setOperations).remove("command_routes:" + commandType, instanceId);
        verify(setOperations).remove("handler_registry:" + instanceId, commandType);
    }
    
    @Test
    void testUpdateInstanceHealth_ShouldUpdateRedisWithExpiration() {
        // Given
        String instanceId = "instance-1";
        String status = "healthy";
        
        // When
        commandRoutingService.updateInstanceHealth(instanceId, status);
        
        // Then
        verify(hashOperations).putAll(eq("instance_health:" + instanceId), any(Map.class));
        verify(redisTemplate).expire("instance_health:" + instanceId, Duration.ofMinutes(2));
    }
    
    @Test
    void testGetCommandTypesForInstance_ShouldReturnCommandTypes() {
        // Given
        String instanceId = "instance-1";
        Set<Object> commandTypes = new HashSet<>(Arrays.asList("CreateUserCommand", "UpdateUserCommand"));
        when(setOperations.members("handler_registry:" + instanceId)).thenReturn(commandTypes);
        
        // When
        Set<String> result = commandRoutingService.getCommandTypesForInstance(instanceId);
        
        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains("CreateUserCommand"));
        assertTrue(result.contains("UpdateUserCommand"));
    }
    
    @Test
    void testGetInstancesForCommandType_ShouldReturnInstances() {
        // Given
        String commandType = "CreateUserCommand";
        Set<Object> instances = new HashSet<>(Arrays.asList("instance-1", "instance-2"));
        when(setOperations.members("command_routes:" + commandType)).thenReturn(instances);
        
        // When
        List<String> result = commandRoutingService.getInstancesForCommandType(commandType);
        
        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains("instance-1"));
        assertTrue(result.contains("instance-2"));
    }
    
    @Test
    void testRemoveInstance_ShouldCleanupAllRoutingInfo() {
        // Given
        String instanceId = "instance-1";
        Set<Object> commandTypes = new HashSet<>(Arrays.asList("CreateUserCommand", "UpdateUserCommand"));
        when(setOperations.members("handler_registry:" + instanceId)).thenReturn(commandTypes);
        
        // When
        commandRoutingService.removeInstance(instanceId);
        
        // Then
        verify(setOperations).remove("command_routes:CreateUserCommand", instanceId);
        verify(setOperations).remove("command_routes:UpdateUserCommand", instanceId);
        verify(setOperations).remove("handler_registry:" + instanceId, "CreateUserCommand");
        verify(setOperations).remove("handler_registry:" + instanceId, "UpdateUserCommand");
        verify(redisTemplate).delete("instance_health:" + instanceId);
        verify(redisTemplate).delete("handler_registry:" + instanceId);
    }
    
    @Test
    void testGetCommandTypesForInstance_WithRedisException_ShouldReturnEmptySet() {
        // Given
        String instanceId = "instance-1";
        when(setOperations.members("handler_registry:" + instanceId))
                .thenThrow(new RuntimeException("Redis connection failed"));
        
        // When
        Set<String> result = commandRoutingService.getCommandTypesForInstance(instanceId);
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testGetInstancesForCommandType_WithRedisException_ShouldReturnEmptyList() {
        // Given
        String commandType = "CreateUserCommand";
        when(setOperations.members("command_routes:" + commandType))
                .thenThrow(new RuntimeException("Redis connection failed"));
        
        // When
        List<String> result = commandRoutingService.getInstancesForCommandType(commandType);
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testRegisterCommandHandler_WithRedisException_ShouldThrowCommandRoutingException() {
        // Given
        String instanceId = "instance-1";
        String commandType = "CreateUserCommand";
        when(setOperations.add(anyString(), any())).thenThrow(new RuntimeException("Redis connection failed"));
        
        // When & Then
        CommandRoutingService.CommandRoutingException exception = assertThrows(
                CommandRoutingService.CommandRoutingException.class,
                () -> commandRoutingService.registerCommandHandler(instanceId, commandType)
        );
        
        assertEquals("Failed to register command handler", exception.getMessage());
    }
    
    @Test
    void testRouteCommand_WithSingleInstance_ShouldReturnThatInstance() {
        // Given
        String commandType = "CreateUserCommand";
        String aggregateId = "user-123";
        
        Set<Object> instances = new HashSet<>(Arrays.asList("instance-1"));
        when(setOperations.members("command_routes:" + commandType)).thenReturn(instances);
        
        Map<Object, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "healthy");
        when(hashOperations.entries("instance_health:instance-1")).thenReturn(healthInfo);
        
        // When
        String result = commandRoutingService.routeCommand(commandType, aggregateId);
        
        // Then
        assertEquals("instance-1", result);
    }
    
    @Test
    void testRouteCommand_WithEmptyInstanceSet_ShouldThrowException() {
        // Given
        String commandType = "CreateUserCommand";
        String aggregateId = "user-123";
        
        when(setOperations.members("command_routes:" + commandType)).thenReturn(new HashSet<>());
        
        // When & Then
        CommandRoutingService.CommandRoutingException exception = assertThrows(
                CommandRoutingService.CommandRoutingException.class,
                () -> commandRoutingService.routeCommand(commandType, aggregateId)
        );
        
        assertEquals("No healthy instances available for command type: " + commandType, exception.getMessage());
    }
}