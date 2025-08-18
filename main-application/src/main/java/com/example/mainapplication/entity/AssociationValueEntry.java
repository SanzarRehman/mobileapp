package com.example.mainapplication.entity;

import jakarta.persistence.*;

/**
 * JPA entity for storing saga association values.
 * Required by Axon Framework's JpaSagaStore for saga lookups.
 */
@Entity
@Table(name = "associationvalueentry")
public class AssociationValueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "association_key", length = 255, nullable = false)
    private String associationKey;

    @Column(name = "association_value", length = 255)
    private String associationValue;

    @Column(name = "saga_type", length = 255, nullable = false)
    private String sagaType;

    @Column(name = "saga_id", length = 255, nullable = false)
    private String sagaId;

    // Default constructor for JPA
    protected AssociationValueEntry() {
    }

    public AssociationValueEntry(String associationKey, String associationValue, 
                               String sagaType, String sagaId) {
        this.associationKey = associationKey;
        this.associationValue = associationValue;
        this.sagaType = sagaType;
        this.sagaId = sagaId;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAssociationKey() {
        return associationKey;
    }

    public void setAssociationKey(String associationKey) {
        this.associationKey = associationKey;
    }

    public String getAssociationValue() {
        return associationValue;
    }

    public void setAssociationValue(String associationValue) {
        this.associationValue = associationValue;
    }

    public String getSagaType() {
        return sagaType;
    }

    public void setSagaType(String sagaType) {
        this.sagaType = sagaType;
    }

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }
}