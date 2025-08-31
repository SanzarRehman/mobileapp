package com.example.mainapplication.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class UpdateUserCommand {
    
    @TargetAggregateIdentifier
    private final String userId;
    private final String username;
    private final String email;
    private final String fullName;

    @JsonCreator
    public UpdateUserCommand(@JsonProperty("userId")String userId,  @JsonProperty("username")String username, @JsonProperty("email")String email, @JsonProperty("fullName")String fullName) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public String getFullName() {
        return fullName;
    }
}