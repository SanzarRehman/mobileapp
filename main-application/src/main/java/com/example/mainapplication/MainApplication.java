package com.example.mainapplication;

import com.example.mainapplication.entity.SagaEntry;
import org.axonframework.eventhandling.tokenstore.jpa.TokenEntry;
import org.axonframework.eventsourcing.eventstore.jpa.DomainEventEntry;
import org.axonframework.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.axonframework.modelling.saga.repository.jpa.AssociationValueEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackageClasses = {
    // Axon entities
    TokenEntry.class,
    DomainEventEntry.class,
    SnapshotEventEntry.class,
    SagaEntry.class,
    AssociationValueEntry.class,
    // (Optional) your own entity root package
    com.example.mainapplication.MainApplication.class
})
public class MainApplication {
    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }
}