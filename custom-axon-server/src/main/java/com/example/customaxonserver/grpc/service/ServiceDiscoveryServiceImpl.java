package com.example.customaxonserver.grpc.service;

import com.example.grpc.common.*;
import com.example.customaxonserver.service.ServiceDiscoveryService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * gRPC service implementation for service discovery operations.
 */
@GrpcService
public class ServiceDiscoveryServiceImpl extends ServiceDiscoveryServiceGrpc.ServiceDiscoveryServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryServiceImpl.class);

    private final ServiceDiscoveryService serviceDiscoveryService;
    
    // Store active service watchers for real-time notifications
    private final ConcurrentMap<String, StreamObserver<ServiceChangeNotification>> serviceWatchers = new ConcurrentHashMap<>();

    @Autowired
    public ServiceDiscoveryServiceImpl(ServiceDiscoveryService serviceDiscoveryService) {
        this.serviceDiscoveryService = serviceDiscoveryService;
    }

    @Override
    public void registerService(RegisterServiceRequest request, StreamObserver<RegisterServiceResponse> responseObserver) {
        logger.info("Registering service: {} instance: {}", 
                   request.getService().getServiceName(), request.getService().getInstanceId());
        
        try {
            serviceDiscoveryService.registerService(request.getService());
            
            // Notify watchers about new service
            notifyServiceWatchers(request.getService(), ChangeType.ADDED);
            
            RegisterServiceResponse response = RegisterServiceResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Service registered successfully")
                    .setRegistrationId(request.getService().getInstanceId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to register service: {}", request.getService().getInstanceId(), e);
            
            RegisterServiceResponse response = RegisterServiceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to register service: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void unregisterService(UnregisterServiceRequest request, StreamObserver<UnregisterServiceResponse> responseObserver) {
        logger.info("Unregistering service instance: {}", request.getInstanceId());
        
        try {
            ServiceInstance serviceInstance = serviceDiscoveryService.getServiceInstance(request.getInstanceId());
            
            serviceDiscoveryService.unregisterService(request.getInstanceId());
            
            // Notify watchers about removed service
            if (serviceInstance != null) {
                notifyServiceWatchers(serviceInstance, ChangeType.REMOVED);
            }
            
            UnregisterServiceResponse response = UnregisterServiceResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Service unregistered successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to unregister service: {}", request.getInstanceId(), e);
            
            UnregisterServiceResponse response = UnregisterServiceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to unregister service: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getHealthyServices(GetHealthyServicesRequest request, StreamObserver<GetHealthyServicesResponse> responseObserver) {
        logger.debug("Getting healthy services for: {}", request.getServiceName());
        
        try {
            List<ServiceInstance> healthyServices = serviceDiscoveryService.getHealthyServices(
                    request.getServiceName(), request.getTagsList());
            
            GetHealthyServicesResponse response = GetHealthyServicesResponse.newBuilder()
                    .addAllServices(healthyServices)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to get healthy services for: {}", request.getServiceName(), e);
            
            GetHealthyServicesResponse response = GetHealthyServicesResponse.newBuilder()
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void watchServices(WatchServicesRequest request, StreamObserver<ServiceChangeNotification> responseObserver) {
        logger.debug("Starting service watch for: {}", request.getServiceName());
        
        String watchKey = request.getServiceName();
        serviceWatchers.put(watchKey, responseObserver);
        
        // Send initial state of all services
        try {
            List<ServiceInstance> allServices = serviceDiscoveryService.getAllServices(request.getServiceName());
            for (ServiceInstance service : allServices) {
                ServiceChangeNotification notification = ServiceChangeNotification.newBuilder()
                        .setService(service)
                        .setChangeType(ChangeType.ADDED)
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                responseObserver.onNext(notification);
            }
        } catch (Exception e) {
            logger.error("Failed to send initial service state for watch: {}", request.getServiceName(), e);
        }
        
        // The stream will be kept alive and updated via notifyServiceWatchers method
    }

    private void notifyServiceWatchers(ServiceInstance serviceInstance, ChangeType changeType) {
        String serviceName = serviceInstance.getServiceName();
        StreamObserver<ServiceChangeNotification> observer = serviceWatchers.get(serviceName);
        
        if (observer != null) {
            try {
                ServiceChangeNotification notification = ServiceChangeNotification.newBuilder()
                        .setService(serviceInstance)
                        .setChangeType(changeType)
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                observer.onNext(notification);
            } catch (Exception e) {
                logger.warn("Failed to notify service watcher for service: {}", serviceName, e);
                // Remove failed observer
                serviceWatchers.remove(serviceName);
            }
        }
    }
}