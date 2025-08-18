package com.example.mainapplication.config;

import com.example.mainapplication.entity.AssociationValueEntry;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.tokenstore.jpa.TokenEntry;
import org.axonframework.eventsourcing.eventstore.jpa.DomainEventEntry;
import org.axonframework.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Configuration for JPA components required by Axon Framework
 * when JPA auto-configuration is excluded.
 */
@Configuration
@EntityScan(basePackageClasses = {
    DomainEventEntry.class,
    SnapshotEventEntry.class,
    TokenEntry.class,
    AssociationValueEntry.class
})
public class AxonJpaConfig {

    /**
     * Provides EntityManagerProvider bean required by Axon's event store.
     */
    @Bean
    public EntityManagerProvider entityManagerProvider(EntityManagerFactory entityManagerFactory) {
        return new EntityManagerProvider() {
            @Override
            public EntityManager getEntityManager() {
                return entityManagerFactory.createEntityManager();
            }
        };
    }

    /**
     * Provides SagaStore bean for persisting saga state.
     * Uses Axon's default serialization to avoid conflicts with other serializers.
     */
    @Bean
    public SagaStore sagaStore(EntityManagerProvider entityManagerProvider) {
        return JpaSagaStore.builder()
                .entityManagerProvider(entityManagerProvider)
                .build();
    }
}