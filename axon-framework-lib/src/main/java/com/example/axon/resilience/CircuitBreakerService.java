package com.example.axon.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Service
public class CircuitBreakerService {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerService.class);
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public CircuitBreakerService() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .build();

        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
    }

    public <T> T executeWithCircuitBreaker(String serviceName, Supplier<T> operation) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
        
        try {
            T result = decoratedSupplier.get();
            logger.debug("Circuit breaker call successful for service: {}", serviceName);
            return result;
        } catch (Exception e) {
            logger.error("Circuit breaker call failed for service: {}, state: {}", 
                    serviceName, circuitBreaker.getState(), e);
            throw e;
        }
    }

    public void executeWithCircuitBreaker(String serviceName, Runnable operation) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(serviceName);
        
        Runnable decoratedRunnable = CircuitBreaker.decorateRunnable(circuitBreaker, operation);
        
        try {
            decoratedRunnable.run();
            logger.debug("Circuit breaker call successful for service: {}", serviceName);
        } catch (Exception e) {
            logger.error("Circuit breaker call failed for service: {}, state: {}", 
                    serviceName, circuitBreaker.getState(), e);
            throw e;
        }
    }

    private CircuitBreaker getOrCreateCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, name -> {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
            
            circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> 
                            logger.info("Circuit breaker state transition for {}: {} -> {}", 
                                    name, event.getStateTransition().getFromState(), 
                                    event.getStateTransition().getToState()));
            
            circuitBreaker.getEventPublisher()
                    .onCallNotPermitted(event -> 
                            logger.warn("Circuit breaker call not permitted for service: {}", name));
            
            return circuitBreaker;
        });
    }

    public CircuitBreaker.State getCircuitBreakerState(String serviceName) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
        return circuitBreaker != null ? circuitBreaker.getState() : null;
    }
}