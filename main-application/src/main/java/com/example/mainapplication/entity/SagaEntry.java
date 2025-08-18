package com.example.mainapplication.entity;

import jakarta.persistence.*;

/**
 * JPA entity for storing saga instances.
 * Required by Axon Framework's JpaSagaStore.
 */
@Entity
@Table(name = "sagaentry")
public class SagaEntry {

    @Id
    @Column(name = "saga_id", length = 255)
    private String sagaId;

    @Column(name = "saga_type", length = 255, nullable = false)
    private String sagaType;

    @Column(name = "revision", length = 255)
    private String revision;

    // Use @Column instead of @Lob for PostgreSQL compatibility
    @Column(name = "serialized_saga", columnDefinition = "bytea")
    private byte[] serializedSaga;

    // Default constructor for JPA
    protected SagaEntry() {
    }

    public SagaEntry(String sagaId, String sagaType, String revision, byte[] serializedSaga) {
        this.sagaId = sagaId;
        this.sagaType = sagaType;
        this.revision = revision;
        this.serializedSaga = serializedSaga;
    }

    // Getters and setters
    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public String getSagaType() {
        return sagaType;
    }

    public void setSagaType(String sagaType) {
        this.sagaType = sagaType;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public byte[] getSerializedSaga() {
        return serializedSaga;
    }

    public void setSerializedSaga(byte[] serializedSaga) {
        this.serializedSaga = serializedSaga;
    }
}