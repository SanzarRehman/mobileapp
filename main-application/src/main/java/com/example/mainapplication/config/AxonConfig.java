package com.example.mainapplication.config;

//import com.example.mainapplication.interceptor.CommandLoggingInterceptor;
import com.example.mainapplication.interceptor.CommandValidationInterceptor;
import com.example.mainapplication.interceptor.SecurityInterceptor;
import com.example.mainapplication.interceptor.EventPublishingInterceptor;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.config.EventProcessingConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Axon Framework configuration for the main application.
 * Configures interceptors to work with Axon's auto-configuration.
 */
@Configuration
public class AxonConfig {

    private final CommandValidationInterceptor validationInterceptor;
  //  private final CommandLoggingInterceptor loggingInterceptor;
    private final SecurityInterceptor securityInterceptor;
    private final EventPublishingInterceptor eventPublishingInterceptor;
    private final CommandBus commandBus;

    public AxonConfig(CommandValidationInterceptor validationInterceptor, 
    //                 CommandLoggingInterceptor loggingInterceptor,
                     SecurityInterceptor securityInterceptor,
                     EventPublishingInterceptor eventPublishingInterceptor,
                     CommandBus commandBus) {
        this.validationInterceptor = validationInterceptor;
  //      this.loggingInterceptor = loggingInterceptor;
        this.securityInterceptor = securityInterceptor;
        this.eventPublishingInterceptor = eventPublishingInterceptor;
        this.commandBus = commandBus;
    }

    /**
     * Configure command interceptors after application startup to avoid circular dependencies.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void configureCommandInterceptors() {
        // Register command interceptors with the auto-configured command bus
//        commandBus.registerDispatchInterceptor(validationInterceptor);
//        commandBus.registerHandlerInterceptor(securityInterceptor);
//        commandBus.registerHandlerInterceptor(loggingInterceptor);
    }

    /**
     * Configure event processing to include event publishing interceptor.
     */
    @Autowired
    public void configureEventProcessing(EventProcessingConfigurer configurer) {
        // Register the event publishing interceptor for all event processors
        configurer.registerDefaultHandlerInterceptor((config, processorName) -> eventPublishingInterceptor);
    }
}