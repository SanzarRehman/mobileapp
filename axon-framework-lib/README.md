## Axon Framework Library

A comprehensive, standalone Spring Boot library that provides **enterprise-grade Axon Framework capabilities** with **high-performance gRPC communication** and **full custom Axon server behavior**. This client library transforms your Spring Boot application into a high-throughput, event-sourced microservice with **zero configuration complexity**.

## 🚀 **Why This Library? Revolutionary Benefits**

### **📈 High TPS (Transactions Per Second) Performance**
- **gRPC Binary Protocol**: 10x faster than REST/HTTP for inter-service communication
- **Optimized Event Routing**: Direct gRPC streams eliminate HTTP overhead
- **Async Processing**: Non-blocking operations with `@Async` and reactive patterns
- **Connection Pooling**: Persistent gRPC channels with keep-alive mechanisms
- **Circuit Breaker Patterns**: Prevents cascade failures, maintains throughput
- **Batch Processing**: Efficient event batching for high-volume scenarios

### **🏗️ Full Custom Axon Server Architecture**
This library provides **complete Axon Server functionality** through a custom implementation:

#### **🎯 Just Annotate and Forget - We Handle Everything!**
```java
@Component
public class UserCommandHandler {
    
    @CommandHandler  // ✅ Just annotate - we auto-register with gRPC server
    public void handle(CreateUserCommand command) {
        // Your business logic here
        // ✅ We handle: routing, persistence, event sourcing, state management
    }
    
    @EventHandler   // ✅ Just annotate - we auto-subscribe to Pulsar topics
    public void on(UserCreatedEvent event) {
        // Your projection logic
        // ✅ We handle: topic creation, deserialization, error handling
    }
    
    @QueryHandler   // ✅ Just annotate - we auto-register query routes
    public UserView handle(FindUserQuery query) {
        // Your query logic
        // ✅ We handle: query routing, load balancing, response marshaling
    }
}
```

**That's it! Everything else is automatic:**
- ✅ **Auto-Registration**: Handlers automatically registered via gRPC
- ✅ **Event Sourcing**: Full CQRS/ES with JPA state persistence
- ✅ **Topic Management**: Pulsar topics auto-created and subscribed
- ✅ **Service Discovery**: Health monitoring and load balancing
- ✅ **Error Handling**: Circuit breakers, retries, compensation
- ✅ **Monitoring**: Metrics, health checks, distributed tracing

### **⚡ Advanced Features That Boost Performance**

#### **1. gRPC-First Communication**
- **Binary Serialization**: Protocol Buffers for minimal payload size
- **HTTP/2 Multiplexing**: Multiple requests over single connection
- **Streaming Support**: Bi-directional streams for real-time updates
- **Load Balancing**: Intelligent routing to healthy instances

#### **2. Intelligent Event Routing**
- **Auto-Discovery**: Handlers automatically discovered via reflection
- **Smart Routing**: Commands routed to specific aggregate instances
- **Event Broadcasting**: Events distributed to all interested handlers
- **Topic Partitioning**: Efficient Pulsar topic management

#### **3. Real Event Sourcing + State Management**
- **Dual Storage**: Events in custom server + JPA projections locally
- **Event Replay**: Full event store with replay capabilities
- **Snapshot Support**: Automatic aggregate snapshots for performance
- **Version Management**: Schema evolution and migration support

#### **4. Auto Pulsar Integration**
```java
// When you create an EventHandler, we automatically:
// ✅ Create Pulsar topic: "UserCreatedEvent"
// ✅ Subscribe with service name: "user-service"  
// ✅ Handle deserialization, error recovery, DLQ
// ✅ Convert to Axon events with metadata
@EventHandler
public void on(UserCreatedEvent event) {
    // Just focus on your business logic!
}
```

## 🏛️ **Architecture Excellence**

### **Distributed Axon Server Behavior**
```
┌─────────────────────────────────────────────────────────────┐
│                 CUSTOM AXON SERVER                          │
│  ┌─────────────────┬─────────────────┬─────────────────┐    │
│  │   Command Bus   │   Event Store   │   Query Bus     │    │
│  │   (gRPC)        │   (Persistent)  │   (gRPC)        │    │
│  └─────────────────┴─────────────────┴─────────────────┘    │
│           │                 │                 │              │
│           ▼                 ▼                 ▼              │
│  ┌─────────────────┬─────────────────┬─────────────────┐    │
│  │ Handler Registry│ Event Sourcing  │ Service Discovery│   │
│  │ Auto-Discovery  │ + Snapshots     │ Health Monitoring│   │
│  └─────────────────┴─────────────────┴─────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
        ┌─────────────────────┐ ┌─────────────────────┐
        │   CLIENT APP 1      │ │   CLIENT APP 2      │
        │ (This Library)      │ │ (This Library)      │
        │                     │ │                     │
        │ @CommandHandler ✅  │ │ @EventHandler ✅    │
        │ @EventHandler ✅    │ │ @QueryHandler ✅    │
        │ @QueryHandler ✅    │ │                     │
        │                     │ │ Auto-Registration   │
        │ Local Projections   │ │ Pulsar Topics       │
        │ JPA State Store     │ │ Circuit Breakers    │
        └─────────────────────┘ └─────────────────────┘
```

### **Why This Delivers Massive TPS**

#### **🔥 Performance Optimizations**
1. **gRPC Streaming**: Sub-millisecond command/event routing
2. **Persistent Connections**: No connection overhead per request
3. **Binary Serialization**: 5-10x smaller payloads than JSON
4. **Async Everything**: Non-blocking I/O throughout the stack
5. **Connection Pooling**: Optimized resource utilization
6. **Smart Caching**: Frequently accessed aggregates cached
7. **Batch Operations**: Multiple events processed in single transaction

#### **🛡️ Enterprise Resilience**
1. **Circuit Breakers**: Auto-failover prevents cascade failures
2. **Retry Mechanisms**: Exponential backoff with jitter
3. **Health Monitoring**: Real-time instance health tracking
4. **Load Balancing**: Intelligent request distribution
5. **Graceful Degradation**: Fallback mechanisms for partial failures

## 🎯 **Business Value Delivered**

### **For Developers: Zero Complexity**
```java
// Before: Complex Axon setup, configuration, infrastructure
// After: Just annotate and deploy!

@SpringBootApplication
public class MyMicroservice {
    // Library auto-configures everything!
    // ✅ Axon Framework
    // ✅ gRPC clients  
    // ✅ Event sourcing
    // ✅ Service discovery
    // ✅ Health monitoring
    // ✅ Metrics & monitoring
}
```

### **For Operations: Production Ready**
- **📊 Built-in Monitoring**: Prometheus metrics, health endpoints
- **🔧 Zero Configuration**: Works out-of-the-box
- **🚀 Horizontal Scaling**: Add instances seamlessly
- **🛡️ Fault Tolerance**: Self-healing architecture
- **📈 Performance Metrics**: Detailed TPS and latency tracking

### **For Business: Speed to Market**
- **⚡ Rapid Development**: Focus on business logic, not infrastructure
- **📈 Elastic Scaling**: Handle traffic spikes automatically  
- **💰 Cost Effective**: Minimal infrastructure overhead
- **🔒 Enterprise Security**: JWT, OAuth2, security patterns built-in

## Features

- **Auto-Configuration**: Automatic Spring Boot integration with zero configuration required
- **gRPC Integration**: Seamless communication with custom Axon servers
- **Event Store Abstraction**: Custom event store implementations with fallback mechanisms
- **Circuit Breaker & Resilience**: Built-in fault tolerance patterns
- **Saga Management**: Advanced saga patterns for distributed transactions
- **Service Discovery**: Automatic handler registration and discovery
- **Metrics & Monitoring**: Comprehensive observability features
- **JWT Authentication**: Keycloak integration for service-to-service communication

## Quick Start

#### 1. Add Dependency

```gradle
dependencies {
    implementation 'com.example:axon-framework-lib:1.0.0-SNAPSHOT'
}
```

#### 2. Enable Auto-Configuration

The library automatically configures itself when included in your Spring Boot application. No additional configuration is required!

#### 3. Optional Configuration

```properties
# Enable/disable the library (default: true)
axon.framework.lib.enabled=true

# gRPC server configuration
grpc.client.custom-axon-server.address=static://localhost:9060

# Custom server configuration
app.custom-server.url=http://localhost:8081
```

## Usage

The library automatically:
- Scans for Axon handlers (`@CommandHandler`, `@EventHandler`, `@QueryHandler`)
- Registers handlers with the custom Axon server
- Provides circuit breaker and retry mechanisms
- Handles service discovery and health monitoring
- Manages projection rebuilding and versioning

## Professional Spring Boot Integration

This library handles complex Spring Boot integration patterns professionally:

- **@ApplicationReadyEvent**: Automatic handler registration after application startup
- **@PreDestroy**: Graceful shutdown and cleanup
- **@Scheduled**: Heartbeat and health monitoring
- **@Async**: Non-blocking operations
- **Component Scanning**: Automatic discovery of library components
- **Conditional Configuration**: Only activates when Axon Framework is present

## Architecture

The library provides a clean separation of concerns:
- **Config Layer**: Auto-configuration and Spring integration
- **Service Layer**: Core business logic and external integrations  
- **Resilience Layer**: Circuit breakers, retries, and fault tolerance
- **Event Store Layer**: Custom event store implementations
- **Utility Layer**: Helper classes and converters

## Spring Boot Compatibility

- Spring Boot 3.2+
- Java 17+
- Axon Framework 4.12+
- gRPC integration
- Supports both standalone and microservice deployments

## 🚀 **Get Started in 30 Seconds**

1. **Add the dependency** ⬆️
2. **Annotate your handlers** with `@CommandHandler`, `@EventHandler`, `@QueryHandler`
3. **Run your app** - Everything else is automatic!

**That's it!** You now have a high-performance, event-sourced microservice with full Axon server capabilities!
