package com.example.mainapplication.resilience;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);
    
    private final RetryRegistry retryRegistry;
    private final ConcurrentMap<String, Retry> retries = new ConcurrentHashMap<>();

    public RetryService() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(attempt -> Duration.ofSeconds((long) Math.pow(2, attempt - 1)).toMillis())
                .retryOnException(this::isRetryableException)
                .build();

        this.retryRegistry = RetryRegistry.of(config);
    }

    public <T> T executeWithRetry(String operationName, Supplier<T> operation) {
        Retry retry = getOrCreateRetry(operationName);
        
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, operation);
        
        try {
            T result = decoratedSupplier.get();
            logger.debug("Retry operation successful for: {}", operationName);
            return result;
        } catch (Exception e) {
            logger.error("Retry operation failed after all attempts for: {}", operationName, e);
            throw e;
        }
    }

    public void executeWithRetry(String operationName, Runnable operation) {
        Retry retry = getOrCreateRetry(operationName);
        
        Runnable decoratedRunnable = Retry.decorateRunnable(retry, operation);
        
        try {
            decoratedRunnable.run();
            logger.debug("Retry operation successful for: {}", operationName);
        } catch (Exception e) {
            logger.error("Retry operation failed after all attempts for: {}", operationName, e);
            throw e;
        }
    }

    private Retry getOrCreateRetry(String operationName) {
        return retries.computeIfAbsent(operationName, name -> {
            Retry retry = retryRegistry.retry(name);
            
            retry.getEventPublisher()
                    .onRetry(event -> 
                            logger.warn("Retry attempt {} for operation: {}, last exception: {}", 
                                    event.getNumberOfRetryAttempts(), name, 
                                    event.getLastThrowable().getMessage()));
            
            retry.getEventPublisher()
                    .onSuccess(event -> 
                            logger.info("Retry operation succeeded for: {} after {} attempts", 
                                    name, event.getNumberOfRetryAttempts()));
            
            retry.getEventPublisher()
                    .onError(event -> 
                            logger.error("Retry operation failed permanently for: {} after {} attempts", 
                                    name, event.getNumberOfRetryAttempts()));
            
            return retry;
        });
    }

    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof DataAccessException ||
               throwable instanceof KafkaException ||
               throwable instanceof ResourceAccessException ||
               throwable instanceof TimeoutException ||
               (throwable instanceof RuntimeException && 
                (throwable.getCause() instanceof java.net.ConnectException ||
                 throwable.getCause() instanceof TimeoutException));
    }
}