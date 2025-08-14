package com.example.mainapplication.config;

import com.example.mainapplication.interceptor.CommandLoggingInterceptor;
import com.example.mainapplication.interceptor.CommandValidationInterceptor;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Axon Framework configuration for the main application.
 * Configures command bus, gateway, and interceptors to work with custom server.
 */
@Configuration
public class AxonConfig {

    private final CommandValidationInterceptor validationInterceptor;
    private final CommandLoggingInterceptor loggingInterceptor;

    public AxonConfig(CommandValidationInterceptor validationInterceptor, 
                     CommandLoggingInterceptor loggingInterceptor) {
        this.validationInterceptor = validationInterceptor;
        this.loggingInterceptor = loggingInterceptor;
    }

    /**
     * Configure command bus with interceptors for validation and logging.
     */
    @Bean
    public CommandBus commandBus() {
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        
        // Add custom validation interceptor
        commandBus.registerDispatchInterceptor(validationInterceptor);
        
        // Add custom logging interceptor
        commandBus.registerHandlerInterceptor(loggingInterceptor);
        
        return commandBus;
    }

    /**
     * Configure command gateway to send commands to custom server.
     */
    @Bean
    public CommandGateway commandGateway(CommandBus commandBus) {
        return DefaultCommandGateway.builder()
                .commandBus(commandBus)
                .build();
    }

    /**
     * RestTemplate for HTTP communication with custom server.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}