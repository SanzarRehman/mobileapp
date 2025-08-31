package com.example.customaxonserver.service;

import com.example.customaxonserver.config.TestRedisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class CommandRoutingServiceIntegrationTest {
    
    @Autowired
    private CommandRoutingService commandRoutingService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    
    @Test
    void testCompleteCommandRoutingFlow() {
        // Given
        String instanceId1 = "instance-1";
        String instanceId2 = "instance-2";
        String commandType = "CreateUserCommand";
        String aggregateId = "user-123";
        
        // When - Register handlers
        commandRoutingService.registerCommandHandler(instanceId1, commandType);
        commandRoutingService.registerCommandHandler(instanceId2, commandType);
        
        // Then - Verify registration
        Set<String> commandTypesForInstance1 = commandRoutingService.getCommandTypesForInstance(instanceId1);
        assertTrue(commandTypesForInstance1.contains(commandType));
        
        List<String> instancesForCommand = commandRoutingService.getInstancesForCommandType(commandType);
        assertEquals(2, instancesForCommand.size());
        assertTrue(instancesForCommand.contains(instanceId1));
        assertTrue(instancesForCommand.contains(instanceId2));
        
        // When - Route command
        String selectedInstance = commandRoutingService.routeCommand(commandType, aggregateId);
        
        // Then - Verify routing
        assertNotNull(selectedInstance);
        assertTrue(instancesForCommand.contains(selectedInstance));
        
        // When - Route same aggregate again
        String selectedInstanceAgain = commandRoutingService.routeCommand(commandType, aggregateId);
        
        // Then - Should get same instance (consistent hashing)
        assertEquals(selectedInstance, selectedInstanceAgain);
    }
    
    @Test
    void testInstanceHealthManagement() {
        // Given
        String instanceId = "instance-1";
        String commandType = "CreateUserCommand";
        String aggregateId = "user-123";
        
        // When - Register handler
        commandRoutingService.registerCommandHandler(instanceId, commandType);
        
        // Then - Should be able to route
        String selectedInstance = commandRoutingService.routeCommand(commandType, aggregateId);
        assertEquals(instanceId, selectedInstance);
        
        // When - Mark instance as unhealthy
        commandRoutingService.updateInstanceHealth(instanceId, "unhealthy");
        
        // Then - Should not be able to route
        CommandRoutingService.CommandRoutingException exception = assertThrows(
                CommandRoutingService.CommandRoutingException.class,
                () -> commandRoutingService.routeCommand(commandType, aggregateId)
        );
        
        assertTrue(exception.getMessage().contains("No healthy instances available"));
        
        // When - Mark instance as healthy again
        commandRoutingService.updateInstanceHealth(instanceId, "healthy");
        
        // Then - Should be able to route again
        String selectedInstanceAfterRecovery = commandRoutingService.routeCommand(commandType, aggregateId);
        assertEquals(instanceId, selectedInstanceAfterRecovery);
    }
    
    @Test
    void testInstanceRemoval() {
        // Given
        String instanceId1 = "instance-1";
        String instanceId2 = "instance-2";
        String commandType = "CreateUserCommand";
        
        // When - Register handlers
        commandRoutingService.registerCommandHandler(instanceId1, commandType);
        commandRoutingService.registerCommandHandler(instanceId2, commandType);
        
        // Then - Both instances should be available
        List<String> instancesBeforeRemoval = commandRoutingService.getInstancesForCommandType(commandType);
        assertEquals(2, instancesBeforeRemoval.size());
        
        // When - Remove one instance
        commandRoutingService.removeInstance(instanceId1);
        
        // Then - Only one instance should remain
        List<String> instancesAfterRemoval = commandRoutingService.getInstancesForCommandType(commandType);
        assertEquals(1, instancesAfterRemoval.size());
        assertFalse(instancesAfterRemoval.contains(instanceId1));
        assertTrue(instancesAfterRemoval.contains(instanceId2));
        
        // And command types for removed instance should be empty
        Set<String> commandTypesForRemovedInstance = commandRoutingService.getCommandTypesForInstance(instanceId1);
        assertTrue(commandTypesForRemovedInstance.isEmpty());
    }
    
    @Test
    void testMultipleCommandTypesPerInstance() {
        // Given
        String instanceId = "instance-1";
        String commandType1 = "CreateUserCommand";
        String commandType2 = "UpdateUserCommand";
        
        // When - Register multiple command types
        commandRoutingService.registerCommandHandler(instanceId, commandType1);
        commandRoutingService.registerCommandHandler(instanceId, commandType2);
        
        // Then - Instance should handle both command types
        Set<String> commandTypes = commandRoutingService.getCommandTypesForInstance(instanceId);
        assertEquals(2, commandTypes.size());
        assertTrue(commandTypes.contains(commandType1));
        assertTrue(commandTypes.contains(commandType2));
        
        // And both command types should route to the instance
        List<String> instancesForCommand1 = commandRoutingService.getInstancesForCommandType(commandType1);
        List<String> instancesForCommand2 = commandRoutingService.getInstancesForCommandType(commandType2);
        
        assertTrue(instancesForCommand1.contains(instanceId));
        assertTrue(instancesForCommand2.contains(instanceId));
    }
    
    @Test
    void testConsistentHashingWithMultipleAggregates() {
        // Given
        String instanceId1 = "instance-1";
        String instanceId2 = "instance-2";
        String commandType = "CreateUserCommand";
        
        commandRoutingService.registerCommandHandler(instanceId1, commandType);
        commandRoutingService.registerCommandHandler(instanceId2, commandType);
        
        // When - Route different aggregates multiple times
        String aggregate1 = "user-123";
        String aggregate2 = "user-456";
        
        String instance1ForAggregate1 = commandRoutingService.routeCommand(commandType, aggregate1);
        String instance2ForAggregate1 = commandRoutingService.routeCommand(commandType, aggregate1);
        String instance3ForAggregate1 = commandRoutingService.routeCommand(commandType, aggregate1);
        
        String instance1ForAggregate2 = commandRoutingService.routeCommand(commandType, aggregate2);
        String instance2ForAggregate2 = commandRoutingService.routeCommand(commandType, aggregate2);
        
        // Then - Same aggregate should always route to same instance
        assertEquals(instance1ForAggregate1, instance2ForAggregate1);
        assertEquals(instance2ForAggregate1, instance3ForAggregate1);
        assertEquals(instance1ForAggregate2, instance2ForAggregate2);
        
        // And both instances should be used (assuming different hash values)
        assertNotNull(instance1ForAggregate1);
        assertNotNull(instance1ForAggregate2);
    }
}