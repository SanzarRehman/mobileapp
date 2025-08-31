package com.example.axon;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.axonframework.config.Configuration;

/**
 * Auto-configuration for Axon Framework Library.
 * This class will be automatically discovered by Spring Boot applications
 * that include this library as a dependency.
 */
@AutoConfiguration
@ConditionalOnClass(Configuration.class)
@EnableConfigurationProperties
@ComponentScan(basePackages = {
    "com.example.axon.service",
    "com.example.axon.eventstore",
    "com.example.axon.resilience",
    "com.example.axon.util"
})
@Import({
    AxonConfig.class,
    EventStoreConfig.class,
    EventProcessorConfig.class,
    RestConfig.class
})
public class AxonFrameworkLibAutoConfiguration {
    // This class serves as the entry point for auto-configuration
    // All beans will be registered through the imported configuration classes
    // and component scanning

    @Bean
    @ConditionalOnMissingBean(AxonHandlerRegistry.class)
    public AxonHandlerRegistry axonHandlerRegistry(ApplicationContext applicationContext) {
        return new AxonHandlerRegistry(applicationContext);
    }
}
