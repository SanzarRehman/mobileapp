package com.example.mainapplication.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class UpdateUserCommand {
    
    @TargetAggregateIdentifier
    private final String userId;
    private final String username;
    private final String email;
    private final String fullName;
    
    public UpdateUserCommand(String userId, String username, String email, String fullName) {
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