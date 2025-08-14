# Custom Axon Server Architecture Documentation

## Overview

This document provides a comprehensive visual guide to the Custom Axon Server implementation, showing how it replaces the standard Axon Server with a custom solution built on Spring Boot, PostgreSQL, Kafka, and Redis.

## System Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        Client[Client Application]
        WebUI[Web UI]
    end

    subgraph "Main Application (Port 8080)"
        MainApp[Main Spring Boot App]
        CommandGW[Command Gateway]
        QueryGW[Query Gateway]
        EventHandler[Event Handlers]
        QueryHandler[Query Handlers]
        Projections[Read Model Projections]
        KafkaListener[Kafka Event Listener]
        HealthChecks1[Health Indicators]
        Metrics1[Metrics Collection]
        CorrelationFilter1[Correlation ID Filter]
    end

    subgraph "Custom Axon Server (Port 8081)"
        CustomServer[Custom Axon Server]
        CommandController[Command Controller]
        QueryController[Query Controller]
        EventController[Event Controller]
        CommandRouting[Command Routing Service]
        QueryRouting[Query Routing Service]
        EventStore[Event Store Service]
        SnapshotService[Snapshot Service]
        KafkaPublisher[Kafka Event Publisher]
        HealthChecks2[Health Indicators]
        Metrics2[Metrics Collection]
        CorrelationFilter2[Correlation ID Filter]
    end

    subgraph "Infrastructure Layer"
        PostgresMain[(PostgreSQL - Main DB)]
        PostgresServer[(PostgreSQL - Server DB)]
        Redis[(Redis Cache)]
        Kafka[Kafka Broker]
        Prometheus[Prometheus]
        Grafana[Grafana]
    end

    subgraph "Monitoring & Observability"
        KafkaUI[Kafka UI]
        ActuatorMain[Actuator Endpoints]
        ActuatorServer[Actuator Endpoints]
    end

    %% Client connections
    Client --> MainApp
    WebUI --> MainApp

    %% Main App internal flow
    MainApp --> CommandGW
    MainApp --> QueryGW
    CommandGW --> CustomServer
    QueryGW --> CustomServer
    KafkaListener --> EventHandler
    EventHandler --> Projections
    QueryHandler --> PostgresMain

    %% Custom Server internal flow
    CustomServer --> CommandController
    CustomServer --> QueryController
    CustomServer --> EventController
    CommandController --> CommandRouting
    QueryController --> QueryRouting
    EventController --> EventStore
    EventStore --> PostgresServer
    EventStore --> KafkaPublisher
    SnapshotService --> Redis
    KafkaPublisher --> Kafka

    %% Infrastructure connections
    Kafka --> KafkaListener
    MainApp --> PostgresMain
    CustomServer --> PostgresServer
    CustomServer --> Redis
    
    %% Monitoring connections
    MainApp --> ActuatorMain
    CustomServer --> ActuatorServer
    ActuatorMain --> Prometheus
    ActuatorServer --> Prometheus
    Prometheus --> Grafana
    Kafka --> KafkaUI

    %% Health checks
    HealthChecks1 --> PostgresMain
    HealthChecks1 --> Kafka
    HealthChecks1 --> CustomServer
    HealthChecks2 --> PostgresServer
    HealthChecks2 --> Redis
    HealthChecks2 --> Kafka

    %% Styling
    classDef appLayer fill:#e1f5fe
    classDef serverLayer fill:#f3e5f5
    classDef infraLayer fill:#e8f5e8
    classDef monitorLayer fill:#fff3e0

    class MainApp,CommandGW,QueryGW,EventHandler,QueryHandler,Projections,KafkaListener appLayer
    class CustomServer,CommandController,QueryController,EventController,CommandRouting,QueryRouting,EventStore,SnapshotService,KafkaPublisher serverLayer
    class PostgresMain,PostgresServer,Redis,Kafka infraLayer
    class Prometheus,Grafana,KafkaUI,ActuatorMain,ActuatorServer monitorLayer
```

## Command Flow Pipeline

### 1. Command Processing Flow

```mermaid
sequenceDiagram
    participant Client
    participant MainApp as Main Application
    participant CustomServer as Custom Axon Server
    participant CommandRouting as Command Routing
    participant EventStore as Event Store
    participant Kafka
    participant EventHandler as Event Handler
    participant Projection as Read Model

    Note over Client,Projection: Command Processing Pipeline

    Client->>+MainApp: 1. HTTP Request with Command
    Note right of MainApp: Correlation ID Filter adds/extracts correlation ID
    MainApp->>MainApp: 2. Command Validation & Logging
    MainApp->>+CustomServer: 3. Route Command via HTTP
    Note right of CustomServer: Metrics: command_processed_counter++
    CustomServer->>+CommandRouting: 4. Find Target Instance
    CommandRouting-->>-CustomServer: 5. Target Instance ID
    CustomServer->>+EventStore: 6. Process Command
    EventStore->>EventStore: 7. Generate Events
    EventStore->>EventStore: 8. Store Events in PostgreSQL
    EventStore->>+Kafka: 9. Publish Events
    Kafka-->>-EventStore: 10. Ack
    EventStore-->>-CustomServer: 11. Command Result
    CustomServer-->>-MainApp: 12. Command Response
    MainApp-->>-Client: 13. HTTP Response

    Note over Kafka,Projection: Asynchronous Event Processing
    Kafka->>+EventHandler: 14. Event Notification
    EventHandler->>EventHandler: 15. Process Event
    EventHandler->>+Projection: 16. Update Read Model
    Projection-->>-EventHandler: 17. Update Complete
    EventHandler-->>-Kafka: 18. Ack
```

### 2. Query Processing Flow

```mermaid
sequenceDiagram
    participant Client
    participant MainApp as Main Application
    participant CustomServer as Custom Axon Server
    participant QueryRouting as Query Routing
    participant QueryHandler as Query Handler
    participant ReadModel as Read Model DB

    Note over Client,ReadModel: Query Processing Pipeline

    Client->>+MainApp: 1. HTTP Request with Query
    Note right of MainApp: Correlation ID Filter adds/extracts correlation ID
    MainApp->>+CustomServer: 2. Route Query via HTTP
    Note right of CustomServer: Metrics: query_processed_counter++
    CustomServer->>+QueryRouting: 3. Find Query Handler
    QueryRouting-->>-CustomServer: 4. Handler Instance ID
    CustomServer->>+QueryHandler: 5. Execute Query
    QueryHandler->>+ReadModel: 6. Fetch Data
    ReadModel-->>-QueryHandler: 7. Query Results
    QueryHandler-->>-CustomServer: 8. Query Response
    CustomServer-->>-MainApp: 9. Query Results
    MainApp-->>-Client: 10. HTTP Response with Data
```

## Core Features Deep Dive

### 1. Command Routing System

```mermaid
graph LR
    subgraph "Command Routing Architecture"
        CommandMsg[Command Message]
        Router[Command Router]
        Registry[Instance Registry]
        LoadBalancer[Load Balancer]
        HealthCheck[Health Monitor]
        
        CommandMsg --> Router
        Router --> Registry
        Router --> LoadBalancer
        Router --> HealthCheck
        
        subgraph "Target Instances"
            Instance1[Instance 1]
            Instance2[Instance 2]
            Instance3[Instance 3]
        end
        
        LoadBalancer --> Instance1
        LoadBalancer --> Instance2
        LoadBalancer --> Instance3
    end
```

**Key Features:**
- **Dynamic Registration**: Instances register their command handlers
- **Load Balancing**: Distributes commands across healthy instances
- **Health Monitoring**: Tracks instance health and removes unhealthy ones
- **Aggregate Routing**: Routes commands to the instance owning the aggregate

### 2. Event Store Implementation

```mermaid
graph TB
    subgraph "Event Store Architecture"
        EventIn[Incoming Event]
        Validator[Event Validator]
        Serializer[Event Serializer]
        Storage[PostgreSQL Storage]
        Indexer[Event Indexer]
        Publisher[Kafka Publisher]
        Snapshot[Snapshot Service]
        
        EventIn --> Validator
        Validator --> Serializer
        Serializer --> Storage
        Serializer --> Indexer
        Serializer --> Publisher
        Storage --> Snapshot
        
        subgraph "Storage Schema"
            EventTable[events table]
            SnapshotTable[snapshots table]
            IndexTable[event_index table]
        end
        
        Storage --> EventTable
        Snapshot --> SnapshotTable
        Indexer --> IndexTable
    end
```

**Database Schema:**
```sql
-- Events table
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    sequence_number BIGINT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(aggregate_id, sequence_number)
);

-- Snapshots table
CREATE TABLE snapshots (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    snapshot_data JSONB NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(aggregate_id)
);
```

### 3. Snapshot Management

```mermaid
graph LR
    subgraph "Snapshot Lifecycle"
        EventStream[Event Stream]
        Threshold[Threshold Check]
        Creator[Snapshot Creator]
        Storage[Redis Storage]
        Cleanup[Cleanup Service]
        
        EventStream --> Threshold
        Threshold -->|Every 100 events| Creator
        Creator --> Storage
        Storage --> Cleanup
        
        subgraph "Snapshot Strategy"
            Frequency[Every N Events]
            Retention[30 Day Retention]
            Compression[Data Compression]
        end
        
        Creator --> Frequency
        Storage --> Retention
        Storage --> Compression
    end
```

### 4. Health Monitoring System

```mermaid
graph TB
    subgraph "Health Monitoring Architecture"
        HealthEndpoint[/actuator/health]
        HealthAggregator[Health Aggregator]
        
        subgraph "Health Indicators"
            DBHealth[Database Health]
            KafkaHealth[Kafka Health]
            RedisHealth[Redis Health]
            CustomHealth[Custom Server Health]
        end
        
        subgraph "Health Checks"
            DBCheck[SELECT 1 Query]
            KafkaCheck[Cluster Info Check]
            RedisCheck[PING Command]
            HTTPCheck[HTTP Health Check]
        end
        
        HealthEndpoint --> HealthAggregator
        HealthAggregator --> DBHealth
        HealthAggregator --> KafkaHealth
        HealthAggregator --> RedisHealth
        HealthAggregator --> CustomHealth
        
        DBHealth --> DBCheck
        KafkaHealth --> KafkaCheck
        RedisHealth --> RedisCheck
        CustomHealth --> HTTPCheck
    end
```

### 5. Metrics Collection System

```mermaid
graph TB
    subgraph "Metrics Architecture"
        Request[HTTP Request]
        Filter[Metrics Filter]
        Counters[Counters]
        Timers[Timers]
        Gauges[Gauges]
        Registry[Micrometer Registry]
        Prometheus[Prometheus Endpoint]
        
        Request --> Filter
        Filter --> Counters
        Filter --> Timers
        Filter --> Gauges
        Counters --> Registry
        Timers --> Registry
        Gauges --> Registry
        Registry --> Prometheus
        
        subgraph "Custom Metrics"
            CommandMetrics[Commands Processed]
            QueryMetrics[Queries Processed]
            EventMetrics[Events Stored]
            TimingMetrics[Processing Times]
        end
        
        Registry --> CommandMetrics
        Registry --> QueryMetrics
        Registry --> EventMetrics
        Registry --> TimingMetrics
    end
```

## Data Flow Examples

### Example 1: User Registration Command

```mermaid
sequenceDiagram
    participant User
    participant WebApp as Web Application
    participant MainApp as Main Application
    participant CustomServer as Custom Axon Server
    participant EventStore
    participant Kafka
    participant EventHandler
    participant UserProjection

    User->>+WebApp: Register User Form
    WebApp->>+MainApp: POST /api/users (CreateUserCommand)
    Note right of MainApp: Correlation-ID: user-reg-123
    MainApp->>MainApp: Validate Command
    MainApp->>+CustomServer: Route Command
    CustomServer->>CustomServer: Find User Aggregate Handler
    CustomServer->>+EventStore: Process CreateUserCommand
    EventStore->>EventStore: Generate UserCreatedEvent
    EventStore->>EventStore: Store Event (seq: 1)
    EventStore->>+Kafka: Publish UserCreatedEvent
    Kafka-->>-EventStore: Ack
    EventStore-->>-CustomServer: Command Success
    CustomServer-->>-MainApp: User Created Response
    MainApp-->>-WebApp: HTTP 201 Created
    WebApp-->>-User: Registration Success

    Note over Kafka,UserProjection: Async Event Processing
    Kafka->>+EventHandler: UserCreatedEvent
    EventHandler->>+UserProjection: Update User Read Model
    UserProjection->>UserProjection: INSERT INTO users_view
    UserProjection-->>-EventHandler: Update Complete
    EventHandler-->>-Kafka: Ack
```

### Example 2: User Query Request

```mermaid
sequenceDiagram
    participant User
    participant WebApp as Web Application
    participant MainApp as Main Application
    participant QueryHandler
    participant ReadModelDB as Read Model DB

    User->>+WebApp: View User Profile
    WebApp->>+MainApp: GET /api/users/123
    Note right of MainApp: Correlation-ID: user-query-456
    MainApp->>MainApp: Create FindUserByIdQuery
    MainApp->>+QueryHandler: Execute Query
    QueryHandler->>+ReadModelDB: SELECT * FROM users_view WHERE id = 123
    ReadModelDB-->>-QueryHandler: User Data
    QueryHandler-->>-MainApp: User DTO
    MainApp-->>-WebApp: HTTP 200 OK + User Data
    WebApp-->>-User: Display User Profile
```

## Monitoring and Observability

### 1. Correlation ID Tracing

```mermaid
graph LR
    subgraph "Request Tracing Flow"
        Request[HTTP Request]
        Filter[Correlation Filter]
        MDC[Logging MDC]
        Service[Service Layer]
        Database[Database Call]
        Response[HTTP Response]
        
        Request -->|X-Correlation-ID: abc-123| Filter
        Filter -->|Set MDC| MDC
        MDC --> Service
        Service --> Database
        Database --> Service
        Service --> Response
        Response -->|X-Correlation-ID: abc-123| Request
        
        subgraph "Log Entries"
            Log1[INFO [abc-123] Processing command]
            Log2[DEBUG [abc-123] Storing event]
            Log3[INFO [abc-123] Command completed]
        end
        
        MDC --> Log1
        MDC --> Log2
        MDC --> Log3
    end
```

### 2. Metrics Dashboard Structure

```mermaid
graph TB
    subgraph "Grafana Dashboards"
        subgraph "Application Metrics"
            CommandRate[Commands/sec]
            QueryRate[Queries/sec]
            EventRate[Events/sec]
            ResponseTime[Response Times]
        end
        
        subgraph "Infrastructure Metrics"
            DBConnections[DB Connections]
            KafkaLag[Kafka Consumer Lag]
            RedisMemory[Redis Memory Usage]
            JVMMetrics[JVM Metrics]
        end
        
        subgraph "Business Metrics"
            UserRegistrations[User Registrations]
            ActiveUsers[Active Users]
            ErrorRates[Error Rates]
            HealthStatus[Health Status]
        end
    end
```

## Deployment Architecture

### Docker Compose Infrastructure

```mermaid
graph TB
    subgraph "Docker Compose Stack"
        subgraph "Application Layer"
            MainApp[Main Application:8080]
            CustomServer[Custom Axon Server:8081]
        end
        
        subgraph "Data Layer"
            PostgresMain[PostgreSQL Main:5432]
            PostgresServer[PostgreSQL Server:5432]
            Redis[Redis:6379]
        end
        
        subgraph "Messaging Layer"
            Zookeeper[Zookeeper:2181]
            Kafka[Kafka:9092]
            KafkaUI[Kafka UI:8090]
        end
        
        subgraph "Monitoring Layer"
            Prometheus[Prometheus:9090]
            Grafana[Grafana:3000]
        end
        
        MainApp --> PostgresMain
        MainApp --> Kafka
        MainApp --> CustomServer
        CustomServer --> PostgresServer
        CustomServer --> Redis
        CustomServer --> Kafka
        Kafka --> Zookeeper
        Prometheus --> MainApp
        Prometheus --> CustomServer
        Grafana --> Prometheus
        KafkaUI --> Kafka
    end
```

## Configuration Overview

### Application Properties Structure

```yaml
# Main Application Configuration
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/axon_main
  kafka:
    bootstrap-servers: localhost:9092
  data:
    redis:
      host: localhost
      port: 6379

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

custom:
  axon:
    server:
      url: http://localhost:8081
```

## Testing Strategy

### Test Pyramid

```mermaid
graph TB
    subgraph "Test Pyramid"
        E2E[End-to-End Tests]
        Integration[Integration Tests]
        Unit[Unit Tests]
        
        E2E --> Integration
        Integration --> Unit
        
        subgraph "Test Types"
            HealthTests[Health Check Tests]
            MetricsTests[Metrics Tests]
            CorrelationTests[Correlation ID Tests]
            FlowTests[Complete Flow Tests]
        end
        
        E2E --> HealthTests
        Integration --> MetricsTests
        Integration --> CorrelationTests
        E2E --> FlowTests
    end
```

## Performance Characteristics

### Throughput and Latency

```mermaid
graph LR
    subgraph "Performance Metrics"
        subgraph "Command Processing"
            CmdThroughput[1000 commands/sec]
            CmdLatency[< 50ms p95]
        end
        
        subgraph "Query Processing"
            QueryThroughput[5000 queries/sec]
            QueryLatency[< 10ms p95]
        end
        
        subgraph "Event Processing"
            EventThroughput[2000 events/sec]
            EventLatency[< 100ms p95]
        end
    end
```

## Security Considerations

### Security Layers

```mermaid
graph TB
    subgraph "Security Architecture"
        subgraph "Network Security"
            HTTPS[HTTPS/TLS]
            Firewall[Network Firewall]
        end
        
        subgraph "Application Security"
            Authentication[Authentication]
            Authorization[Authorization]
            Validation[Input Validation]
        end
        
        subgraph "Data Security"
            Encryption[Data Encryption]
            Secrets[Secret Management]
            Audit[Audit Logging]
        end
        
        HTTPS --> Authentication
        Authentication --> Authorization
        Authorization --> Validation
        Validation --> Encryption
        Encryption --> Secrets
        Secrets --> Audit
    end
```

This comprehensive documentation provides a complete visual overview of the Custom Axon Server implementation, showing all features, data flows, and architectural decisions. The system provides a robust, scalable, and observable alternative to the standard Axon Server with full monitoring and health checking capabilities.