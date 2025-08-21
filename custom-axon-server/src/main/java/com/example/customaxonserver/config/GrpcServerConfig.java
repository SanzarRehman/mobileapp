package com.example.customaxonserver.config;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

import io.grpc.ServerInterceptor;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ForwardingServerCall;
import io.grpc.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for gRPC server settings and security.
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class GrpcServerConfig {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServerConfig.class);

    /**
     * Global gRPC server interceptor for logging and monitoring.
     */
    @Bean
    @GrpcGlobalServerInterceptor
    public ServerInterceptor loggingInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                
                String methodName = call.getMethodDescriptor().getFullMethodName();
                logger.debug("gRPC call started: {}", methodName);
                
                return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    @Override
                    public void sendMessage(RespT message) {
                        logger.trace("gRPC response sent for: {}", methodName);
                        super.sendMessage(message);
                    }

                    @Override
                    public void close(Status status, Metadata trailers) {
                        if (!status.isOk()) {
                            logger.error("gRPC call failed: {} - {}", methodName, status.getDescription());
                        } else {
                            logger.debug("gRPC call completed successfully: {}", methodName);
                        }
                        super.close(status, trailers);
                    }
                }, headers);
            }
        };
    }
}