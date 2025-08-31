# Spring Boot Auto-Configuration Usage

## How to Use This Library

This library is configured as a Spring Boot auto-configuration library. When you include it as a dependency in another Spring Boot application, it will automatically configure all the Axon Framework components and services.

### Adding as Dependency

Add this to your consuming application's `build.gradle`:

```gradle
dependencies {
    implementation 'com.example:axon-framework-lib:1.0.0-SNAPSHOT'
}
```

Or in Maven `pom.xml`:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>axon-framework-lib</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Auto-Configuration

The library will automatically configure:

- **Axon Framework Components**: Event store, event bus, command bus, query bus
- **Custom Event Store**: Routes events to your custom server
- **gRPC Services**: Command handler registration and streaming
- **Security**: JWT authentication service
- **Resilience**: Circuit breaker and retry services
- **Monitoring**: Metrics and health checks
- **Message Processing**: Pulsar integration for event streaming

### Configuration Properties

You can customize the library behavior using application properties:

```properties
# gRPC Server Configuration
app.grpc-server.url=localhost
app.grpc-server.port=9060

# JWT Configuration
jwt.secret=your-secret-key
jwt.expiration=86400000

# Axon Configuration
axon.eventhandling.processors.tracking-event-processor.mode=tracking
```

### Component Scanning

The auto-configuration automatically scans these packages:
- `com.example.axon.service`
- `com.example.axon.eventstore`
- `com.example.axon.resilience`
- `com.example.axon.util`

### Disabling Auto-Configuration

If you need to disable specific parts, you can exclude them:

```java
@SpringBootApplication(exclude = {AxonFrameworkLibAutoConfiguration.class})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### Available Beans

The library provides these beans that you can inject:

- `EventStore`: Custom event store that forwards to your server
- `RestTemplate`: Configured REST template
- `CircuitBreakerService`: For resilience patterns
- `RetryService`: For retry logic
- `JwtAuthenticationService`: For JWT token management
- `GrpcCommandHandlerRegistrationService`: For command registration
- Various other utility services

### Usage Example

```java
@RestController
public class MyController {
    
    @Autowired
    private CommandGateway commandGateway;
    
    @Autowired
    private QueryGateway queryGateway;
    
    @Autowired
    private CircuitBreakerService circuitBreakerService;
    
    @PostMapping("/command")
    public void sendCommand(@RequestBody MyCommand command) {
        circuitBreakerService.executeWithCircuitBreaker("command-service", 
            () -> commandGateway.send(command));
    }
}
```
