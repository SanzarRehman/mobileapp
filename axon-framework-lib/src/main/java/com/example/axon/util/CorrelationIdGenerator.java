package com.example.axon.util;

import java.util.UUID;

public class CorrelationIdGenerator {
    
    public static String generate() {
        return UUID.randomUUID().toString();
    }
    
    public static String generateShort() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}