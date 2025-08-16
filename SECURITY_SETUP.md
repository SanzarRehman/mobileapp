# Security Setup Guide

This guide explains how to set up JWT-based authentication with Keycloak for the Axon Framework Custom Server project.

## Overview

The security implementation includes:
- JWT-based authentication using Keycloak
- Role-based authorization (USER, ADMIN, SERVICE)
- Service-to-service authentication
- Secure API endpoints

## Quick Start

1. **Start the infrastructure:**
   ```bash
   docker-compose up -d postgres redis kafka keycloak
   ```

2. **Wait for Keycloak to start** (check health with `docker-compose ps`)

3. **Access Keycloak Admin Console:**
   - URL: http://localhost:8180/auth/admin
   - Username: `admin`
   - Password: `admin`

4. **Import the realm configuration:**
   - Go to "Add realm" → "Select file" → Choose `docker/keycloak/axon-realm.json`
   - Click "Create"

## Keycloak Configuration

### Realm: axon-realm

The realm includes:

#### Clients:
- **main-application**: Client for the main Spring Boot application
  - Client ID: `main-application`
  - Client Secret: `main-app-secret`
  - Bearer Only: true
  
- **custom-axon-server**: Client for the custom Axon server
  - Client ID: `custom-axon-server`
  - Client Secret: `server-secret`
  - Bearer Only: true

#### Roles:
- **USER**: Standard user role for accessing application features
- **ADMIN**: Administrator role with full access
- **SERVICE**: Service role for inter-service communication

#### Test Users:
- **testuser** / **password** (Role: USER)
- **admin** / **admin** (Roles: USER, ADMIN)

## Application Configuration

### Environment Variables

Set these environment variables for production:

```bash
# Main Application
KEYCLOAK_CLIENT_SECRET=your-main-app-secret

# Custom Axon Server
KEYCLOAK_CLIENT_SECRET=your-server-secret
```

### Configuration Files

The applications are configured to use Keycloak in `application.yml`:

```yaml
keycloak:
  auth-server-url: http://localhost:8180/auth
  realm: axon-realm
  resource: main-application  # or custom-axon-server
  credentials:
    secret: ${KEYCLOAK_CLIENT_SECRET:your-client-secret}
  use-resource-role-mappings: true
  bearer-only: true
```

## API Security

### Main Application Endpoints:

- **Public**: `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`
- **USER role**: `/api/commands/**`, `/api/queries/**`
- **ADMIN role**: `/api/projections/**`

### Custom Axon Server Endpoints:

- **Public**: `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`
- **SERVICE/ADMIN roles**: `/api/commands/**`, `/api/queries/**`, `/api/events/**`, `/api/snapshots/**`
- **ADMIN role**: `/api/admin/**`

## Getting JWT Tokens

### For Testing (using curl):

```bash
# Get token for testuser
curl -X POST http://localhost:8180/auth/realms/axon-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=main-application" \
  -d "client_secret=main-app-secret" \
  -d "username=testuser" \
  -d "password=password"

# Get service token (client credentials)
curl -X POST http://localhost:8180/auth/realms/axon-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=main-application" \
  -d "client_secret=main-app-secret"
```

### Using the JWT Token:

```bash
# Use the access_token from the response
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8080/api/commands/users
```

## Service-to-Service Authentication

The `JwtAuthenticationService` automatically handles service-to-service authentication:

- Requests JWT tokens using client credentials flow
- Caches tokens until expiration
- Automatically adds tokens to outgoing HTTP requests
- Handles token refresh

## Testing

### Unit Tests

Security is disabled in tests using `TestSecurityConfig`:

```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class MyTest {
    // Tests run without security
}
```

### Integration Tests

For integration tests that need authentication:

```java
@Test
@WithMockUser(roles = "USER")
void testWithAuthentication() {
    // Test with mocked authentication
}
```

## Troubleshooting

### Common Issues:

1. **Keycloak not accessible**: Check if container is running and healthy
2. **Invalid token**: Verify client secrets and realm configuration
3. **403 Forbidden**: Check user roles and endpoint security configuration
4. **Token expired**: Tokens expire after 1 hour by default

### Logs:

Enable debug logging for security:

```yaml
logging:
  level:
    org.springframework.security: DEBUG
    org.keycloak: DEBUG
```

## Production Considerations

1. **Use HTTPS** in production
2. **Change default passwords** and client secrets
3. **Configure proper token lifespans**
4. **Set up Keycloak clustering** for high availability
5. **Use external database** for Keycloak (not the same as application DB)
6. **Configure proper CORS** settings
7. **Set up monitoring** for authentication failures

## Manual Keycloak Setup (Alternative)

If you prefer manual setup instead of importing the realm:

1. Create realm "axon-realm"
2. Create clients with the configurations above
3. Create roles: USER, ADMIN, SERVICE
4. Create test users and assign roles
5. Configure client mappers for realm roles