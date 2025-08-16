package com.example.mainapplication.config;

import com.example.mainapplication.interceptor.CommandLoggingInterceptor;
import com.example.mainapplication.interceptor.CommandValidationInterceptor;
import com.example.mainapplication.interceptor.SecurityInterceptor;
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
    private final SecurityInterceptor securityInterceptor;

    public AxonConfig(CommandValidationInterceptor validationInterceptor, 
                     CommandLoggingInterceptor loggingInterceptor,
                     SecurityInterceptor securityInterceptor) {
        this.validationInterceptor = validationInterceptor;
        this.loggingInterceptor = loggingInterceptor;
        this.securityInterceptor = securityInterceptor;
    }

    /**
     * Configure command bus with interceptors for validation and logging.
     */
    @Bean
    public CommandBus commandBus() {
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        
        // Add custom validation interceptor
        commandBus.registerDispatchInterceptor(validationInterceptor);
        
        // Add security interceptor
        commandBus.registerHandlerInterceptor(securityInterceptor);
        
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


}