package com.example.axon;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for Saga management.
 * Configures saga storage and serialization.
 */
@Configuration
public class RestConfig {

    /**
     * JPA-based saga store for persisting saga state.
     * Temporarily disabled to resolve entity mapping issues.
     */
    // @Bean
    // public SagaStore sagaStore(EntityManagerFactory entityManagerFactory, Serializer serializer) {
    //     return JpaSagaStore.builder()
    //             .entityManagerProvider(() -> entityManagerFactory.createEntityManager())
    //             .serializer(serializer)
    //             .build();
    // }
    @Bean
    public RestTemplate restTemplate() {
      return new RestTemplate();
    }


}