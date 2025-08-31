package com.example.customaxonserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service for cleaning up expired service instances and maintaining registry health.
 */
@Service
public class ServiceDiscoveryCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryCleanupService.class);

    private final ServiceDiscoveryService serviceDiscoveryService;
    private final CommandRoutingService commandRoutingService;

    @Autowired
    public ServiceDiscoveryCleanupService(ServiceDiscoveryService serviceDiscoveryService,
                                        CommandRoutingService commandRoutingService) {
        this.serviceDiscoveryService = serviceDiscoveryService;
        this.commandRoutingService = commandRoutingService;
    }

    /**
     * Clean up expired service instances every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredServices() {
        try {
            serviceDiscoveryService.cleanupExpiredServices();
        } catch (Exception e) {
            logger.error("Failed to cleanup expired services", e);
        }
    }

    /**
     * Clean up stale command routing entries every 2 minutes.
     */
    @Scheduled(fixedRate = 120000)
    public void cleanupStaleCommandRoutes() {
        try {
            // This would clean up command routes for instances that no longer exist
            // Implementation would check for instances in command routing that don't exist in service discovery
            logger.debug("Cleaning up stale command routes");
            // Additional cleanup logic can be added here
        } catch (Exception e) {
            logger.error("Failed to cleanup stale command routes", e);
        }
    }
}
