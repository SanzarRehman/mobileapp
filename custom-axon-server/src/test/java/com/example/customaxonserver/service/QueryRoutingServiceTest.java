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

@ExtendWith(MockitoExtension.class)
class QueryRoutingServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ObjectMapper objectMapper;
    private QueryRoutingService queryRoutingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        queryRoutingService = new QueryRoutingService(redisTemplate, objectMapper);
    }

    @Test
    void routeQuery_WithHealthyInstances_ShouldReturnInstance() {
        // Given
        String queryType = "FindUserQuery";
        Set<Object> instances = Set.of("instance-1", "instance-2");
        Map<Object, Object> healthInfo = Map.of("status", "healthy");

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.members("query_routes:" + queryType)).thenReturn(instances);
        when(hashOperations.entries("query_instance_health:instance-1")).thenReturn(healthInfo);
        when(hashOperations.entries("query_instance_health:instance-2")).thenReturn(healthInfo);

        // When
        String result = queryRoutingService.routeQuery(queryType);

        // Then
        assertNotNull(result);
        assertTrue(Set.of("instance-1", "instance-2").contains(result));
    }

    @Test
    void routeQuery_WithNoHealthyInstances_ShouldThrowException() {
        // Given
        String queryType = "FindUserQuery";
        Set<Object> instances = Set.of("instance-1");
        Map<Object, Object> healthInfo = Map.of("status", "unhealthy");

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.members("query_routes:" + queryType)).thenReturn(instances);
        when(hashOperations.entries("query_instance_health:instance-1")).thenReturn(healthInfo);

        // When & Then
        QueryRoutingService.QueryRoutingException exception = assertThrows(
                QueryRoutingService.QueryRoutingException.class,
                () -> queryRoutingService.routeQuery(queryType)
        );

        assertEquals("No healthy instances available for query type: " + queryType, exception.getMessage());
    }

    @Test
    void routeQuery_WithNoInstances_ShouldThrowException() {
        // Given
        String queryType = "FindUserQuery";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("query_routes:" + queryType)).thenReturn(Collections.emptySet());

        // When & Then
        QueryRoutingService.QueryRoutingException exception = assertThrows(
                QueryRoutingService.QueryRoutingException.class,
                () -> queryRoutingService.routeQuery(queryType)
        );

        assertEquals("No healthy instances available for query type: " + queryType, exception.getMessage());
    }

    @Test
    void registerQueryHandler_ShouldAddToRoutesAndRegistry() {
        // Given
        String instanceId = "instance-1";
        String queryType = "FindUserQuery";

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // When
        queryRoutingService.registerQueryHandler(instanceId, queryType);

        // Then
        verify(setOperations).add("query_routes:" + queryType, instanceId);
        verify(setOperations).add("query_handler_registry:" + instanceId, queryType);
        verify(hashOperations).putAll(eq("query_instance_health:" + instanceId), any(Map.class));
        verify(redisTemplate).expire(eq("query_instance_health:" + instanceId), any(Duration.class));
    }

    @Test
    void unregisterQueryHandler_ShouldRemoveFromRoutesAndRegistry() {
        // Given
        String instanceId = "instance-1";
        String queryType = "FindUserQuery";

        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        // When
        queryRoutingService.unregisterQueryHandler(instanceId, queryType);

        // Then
        verify(setOperations).remove("query_routes:" + queryType, instanceId);
        verify(setOperations).remove("query_handler_registry:" + instanceId, queryType);
    }

    @Test
    void updateInstanceHealth_ShouldUpdateHealthInfo() {
        // Given
        String instanceId = "instance-1";
        String status = "healthy";

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // When
        queryRoutingService.updateInstanceHealth(instanceId, status);

        // Then
        verify(hashOperations).putAll(eq("query_instance_health:" + instanceId), any(Map.class));
        verify(redisTemplate).expire(eq("query_instance_health:" + instanceId), any(Duration.class));
    }

    @Test
    void getQueryTypesForInstance_ShouldReturnQueryTypes() {
        // Given
        String instanceId = "instance-1";
        Set<Object> queryTypes = Set.of("FindUserQuery", "FindOrderQuery");

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("query_handler_registry:" + instanceId)).thenReturn(queryTypes);

        // When
        Set<String> result = queryRoutingService.getQueryTypesForInstance(instanceId);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains("FindUserQuery"));
        assertTrue(result.contains("FindOrderQuery"));
    }

    @Test
    void getQueryTypesForInstance_WithException_ShouldReturnEmptySet() {
        // Given
        String instanceId = "instance-1";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("query_handler_registry:" + instanceId))
                .thenThrow(new RuntimeException("Redis error"));

        // When
        Set<String> result = queryRoutingService.getQueryTypesForInstance(instanceId);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void getInstancesForQueryType_ShouldReturnInstances() {
        // Given
        String queryType = "FindUserQuery";
        Set<Object> instances = Set.of("instance-1", "instance-2");

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("query_routes:" + queryType)).thenReturn(instances);

        // When
        List<String> result = queryRoutingService.getInstancesForQueryType(queryType);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains("instance-1"));
        assertTrue(result.contains("instance-2"));
    }

    @Test
    void getInstancesForQueryType_WithException_ShouldReturnEmptyList() {
        // Given
        String queryType = "FindUserQuery";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("query_routes:" + queryType))
                .thenThrow(new RuntimeException("Redis error"));

        // When
        List<String> result = queryRoutingService.getInstancesForQueryType(queryType);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void getAllInstancesHealth_ShouldReturnHealthMap() {
        // Given
        Set<String> keys = Set.of("query_instance_health:instance-1", "query_instance_health:instance-2");
        Map<Object, Object> healthInfo1 = Map.of("status", "healthy");
        Map<Object, Object> healthInfo2 = Map.of("status", "unhealthy");

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.keys("query_instance_health:*")).thenReturn(keys);
        when(hashOperations.entries("query_instance_health:instance-1")).thenReturn(healthInfo1);
        when(hashOperations.entries("query_instance_health:instance-2")).thenReturn(healthInfo2);

        // When
        Map<String, String> result = queryRoutingService.getAllInstancesHealth();

        // Then
        assertEquals(2, result.size());
        assertEquals("healthy", result.get("instance-1"));
        assertEquals("unhealthy", result.get("instance-2"));
    }

    @Test
    void getAllInstancesHealth_WithException_ShouldReturnEmptyMap() {
        // Given
        when(redisTemplate.keys("query_instance_health:*"))
                .thenThrow(new RuntimeException("Redis error"));

        // When
        Map<String, String> result = queryRoutingService.getAllInstancesHealth();

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void removeInstance_ShouldCleanupAllData() {
        // Given
        String instanceId = "instance-1";
        Set<Object> queryTypes = Set.of("FindUserQuery", "FindOrderQuery");

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("query_handler_registry:" + instanceId)).thenReturn(queryTypes);

        // When
        queryRoutingService.removeInstance(instanceId);

        // Then
        verify(setOperations).remove("query_routes:FindUserQuery", instanceId);
        verify(setOperations).remove("query_routes:FindOrderQuery", instanceId);
        verify(setOperations).remove("query_handler_registry:" + instanceId, "FindUserQuery");
        verify(setOperations).remove("query_handler_registry:" + instanceId, "FindOrderQuery");
        verify(redisTemplate).delete("query_instance_health:" + instanceId);
        verify(redisTemplate).delete("query_handler_registry:" + instanceId);
    }

    @Test
    void removeInstance_WithException_ShouldThrowQueryRoutingException() {
        // Given
        String instanceId = "instance-1";
        Set<String> queryTypes = Set.of("FindUserQuery");
        
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("query_handler_registry:" + instanceId)).thenReturn(Set.of("FindUserQuery"));
        when(redisTemplate.delete("query_instance_health:" + instanceId))
                .thenThrow(new RuntimeException("Redis error"));

        // When & Then
        QueryRoutingService.QueryRoutingException exception = assertThrows(
                QueryRoutingService.QueryRoutingException.class,
                () -> queryRoutingService.removeInstance(instanceId)
        );

        assertEquals("Failed to remove instance routing information", exception.getMessage());
    }

    @Test
    void registerQueryHandler_WithException_ShouldThrowQueryRoutingException() {
        // Given
        String instanceId = "instance-1";
        String queryType = "FindUserQuery";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add("query_routes:" + queryType, instanceId))
                .thenThrow(new RuntimeException("Redis error"));

        // When & Then
        QueryRoutingService.QueryRoutingException exception = assertThrows(
                QueryRoutingService.QueryRoutingException.class,
                () -> queryRoutingService.registerQueryHandler(instanceId, queryType)
        );

        assertEquals("Failed to register query handler", exception.getMessage());
    }

    @Test
    void unregisterQueryHandler_WithException_ShouldThrowQueryRoutingException() {
        // Given
        String instanceId = "instance-1";
        String queryType = "FindUserQuery";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.remove("query_routes:" + queryType, instanceId))
                .thenThrow(new RuntimeException("Redis error"));

        // When & Then
        QueryRoutingService.QueryRoutingException exception = assertThrows(
                QueryRoutingService.QueryRoutingException.class,
                () -> queryRoutingService.unregisterQueryHandler(instanceId, queryType)
        );

        assertEquals("Failed to unregister query handler", exception.getMessage());
    }
}