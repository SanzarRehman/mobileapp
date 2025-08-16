package com.example.mainapplication.config;

import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.serialization.Serializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManagerFactory;

/**
 * Configuration for Saga management.
 * Configures saga storage and serialization.
 */
@Configuration
public class SagaConfig {

    /**
     * JPA-based saga store for persisting saga state.
     */
    @Bean
    public SagaStore sagaStore(EntityManagerFactory entityManagerFactory, Serializer serializer) {
        return JpaSagaStore.builder()
                .entityManagerProvider(() -> entityManagerFactory.createEntityManager())
                .serializer(serializer)
                .build();
    }
}