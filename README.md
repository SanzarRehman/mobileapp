# Axon Framework with Custom Server

This project implements a complete Axon Framework solution with a custom server implementation that replaces the traditional Axon Server.

## Architecture

The system consists of two main Spring Boot applications:

1. **Main Application** (`main-application`): Uses Axon Framework for CQRS/Event Sourcing
2. **Custom Axon Server** (`custom-axon-server`): Provides event store, command routing, and query handling

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Docker and Docker Compose

## Quick Start

1. **Start the infrastructure services:**
   ```bash
   docker-compose up -d
   ```

2. **Build the applications:**
   ```bash
   mvn clean install
   ```

3. **Run the Custom Axon Server:**
   ```bash
   cd custom-axon-server
   mvn spring-boot:run
   ```

4. **Run the Main Application:**
   ```bash
   cd main-application
   mvn spring-boot:run
   ```

## Services

- **Main Application**: http://localhost:8080
- **Custom Axon Server**: http://localhost:8081
- **Kafka UI**: http://localhost:8090
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

## Infrastructure

- **PostgreSQL**: localhost:5432
  - Database: `axon_main` (for main application)
  - Database: `axon_server` (for custom server)
  - Username: `axon_user`
  - Password: `axon_password`

- **Redis**: localhost:6379
  - Database 0: Main application
  - Database 1: Custom server

- **Kafka**: localhost:9092

## Development

### Running Tests
```bash
mvn test
```

### Building Docker Images
```bash
# Build main application
cd main-application
mvn spring-boot:build-image

# Build custom server
cd custom-axon-server
mvn spring-boot:build-image
```

### Stopping Services
```bash
docker-compose down
```

### Cleaning Up
```bash
docker-compose down -v  # Removes volumes as well
```

## Monitoring

- Health checks are available at `/actuator/health` for both applications
- Metrics are exposed at `/actuator/prometheus` for Prometheus scraping
- Grafana dashboards can be configured to visualize the metrics