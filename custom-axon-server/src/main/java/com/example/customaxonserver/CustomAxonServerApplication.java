package com.example.customaxonserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.example.customaxonserver", "com.example.pulser"})
public class CustomAxonServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomAxonServerApplication.class, args);
    }
}