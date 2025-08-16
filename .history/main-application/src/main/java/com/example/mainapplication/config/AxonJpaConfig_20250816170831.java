package com.example.mainapplication.config;

import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManagerFactory;

/**
 * Configuration for JPA components required by Axon Framework
 * when JPA auto-configuration is excluded.
 */
@Configuration
public class AxonJpaConfig {

    /**
     * Provides EntityManagerProvider bean required by Axon's event store.
     */
    @Bean
    public EntityManagerProvider entityManagerProvider(EntityManagerFactory entityManagerFactory) {
        return new SimpleEntityManagerProvider(entityManagerFactory);
    }
}