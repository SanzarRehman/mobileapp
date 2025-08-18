package com.example.mainapplication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for registering the main application as a command handler
 * with the custom axon server and maintaining health status.
 */
@Service
@EnableScheduling
public class CommandHandlerRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandlerRegistrationService.class);

    private final RestTemplate restTemplate;
    private final String customServerUrl;
    private final String instanceId;
    
    // Command types that this instance can handle
    private final List<String> supportedCommandTypes = Arrays.asList(
        "com.example.mainapplication.command.CreateUserCommand",
        "com.example.mainapplication.command.UpdateUserCommand"
    );

    public CommandHandlerRegistrationService(
            @Qualifier("restTemplate") RestTemplate restTemplate,
            @Value("${app.custom-server.url:http://localhost:8081}") String customServerUrl,
            @Value("${spring.application.name:main-application}") String applicationName) {
        this.restTemplate = restTemplate;
        this.customServerUrl = customServerUrl;
        this.instanceId = generateInstanceId(applicationName);
    }

    /**
     * Register command handlers when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        logger.info("Application ready, registering command handlers with custom server...");
        
        CompletableFuture.runAsync(() -> {
            try {
                // Wait a bit for the custom server to be ready
                Thread.sleep(5000);
                registerAllCommandHandlers();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Registration interrupted during startup delay");
            } catch (Exception e) {
                logger.error("Failed to register command handlers on startup: {}", e.getMessage(), e);
                // Schedule retry
                scheduleRetryRegistration();
            }
        });
    }

    /**
     * Register all supported command handlers with the custom server.
     */
    private void registerAllCommandHandlers() {
        logger.info("Registering {} command handlers for instance {}", 
                   supportedCommandTypes.size(), instanceId);

        boolean allRegistered = true;
        for (String commandType : supportedCommandTypes) {
            try {
                registerCommandHandler(commandType);
                logger.debug("Successfully registered handler for: {}", commandType);
            } catch (Exception e) {
                logger.error("Failed to register handler for {}: {}", commandType, e.getMessage());
                allRegistered = false;
            }
        }

        if (allRegistered) {
            logger.info("All command handlers registered successfully for instance {}", instanceId);
        } else {
            logger.warn("Some command handlers failed to register. Will retry on next heartbeat.");
        }
    }

    /**
     * Register a single command handler with the custom server.
     */
    private void registerCommandHandler(String commandType) {
        String url = customServerUrl + "/api/commands/handlers/" + instanceId + "/" + commandType;
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                logger.debug("Handler registered for {} on instance {}", commandType, instanceId);
            } else {
                throw new RuntimeException("Registration failed with status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("Failed to register handler for {} on instance {}: {}", 
                        commandType, instanceId, e.getMessage());
            throw e;
        }
    }

    /**
     * Send periodic heartbeat to maintain healthy status.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        try {
            String url = customServerUrl + "/api/commands/instances/" + instanceId + "/health?status=healthy";
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                logger.trace("Heartbeat sent successfully for instance {}", instanceId);
            } else {
                logger.warn("Heartbeat failed with status: {} for instance {}", 
                           response.getStatusCode(), instanceId);
                // Try to re-register handlers
                scheduleRetryRegistration();
            }
        } catch (Exception e) {
            logger.error("Failed to send heartbeat for instance {}: {}", instanceId, e.getMessage());
            // Try to re-register handlers on next heartbeat
            scheduleRetryRegistration();
        }
    }

    /**
     * Check if custom server is available and re-register if needed.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    public void healthCheck() {
        try {
            String healthUrl = customServerUrl + "/actuator/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                // Server is healthy, ensure we're registered
                logger.trace("Custom server health check passed");
                // Optionally re-register if we suspect we're not registered
                // This is a safety net in case registration was lost
            } else {
                logger.warn("Custom server health check failed: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            logger.warn("Custom server health check failed: {}", e.getMessage());
        }
    }

    /**
     * Schedule a retry of command handler registration.
     */
    private void scheduleRetryRegistration() {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(10000); // Wait 10 seconds before retry
                registerAllCommandHandlers();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Retry registration failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Unregister command handlers when the application shuts down.
     */
    @PreDestroy
    public void onShutdown() {
        logger.info("Application shutting down, unregistering command handlers...");
        
        for (String commandType : supportedCommandTypes) {
            try {
                unregisterCommandHandler(commandType);
            } catch (Exception e) {
                logger.warn("Failed to unregister handler for {}: {}", commandType, e.getMessage());
            }
        }
    }

    /**
     * Unregister a command handler from the custom server.
     */
    private void unregisterCommandHandler(String commandType) {
        String url = customServerUrl + "/api/commands/handlers/" + instanceId + "/" + commandType;
        
        try {
            restTemplate.delete(url);
            logger.debug("Handler unregistered for {} on instance {}", commandType, instanceId);
        } catch (Exception e) {
            logger.error("Failed to unregister handler for {} on instance {}: {}", 
                        commandType, instanceId, e.getMessage());
        }
    }

    /**
     * Generate a unique instance ID for this application instance.
     */
    private String generateInstanceId(String applicationName) {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String port = System.getProperty("server.port", "8080");
            return applicationName + "-" + hostname + "-" + port + "-" + System.currentTimeMillis();
        } catch (Exception e) {
            logger.warn("Failed to get hostname, using fallback instance ID");
            return applicationName + "-" + System.currentTimeMillis();
        }
    }

    /**
     * Get the instance ID for this application.
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Get the list of supported command types.
     */
    public List<String> getSupportedCommandTypes() {
        return supportedCommandTypes;
    }

    /**
     * Manually trigger registration (useful for testing).
     */
    public void forceRegistration() {
        registerAllCommandHandlers();
    }
}