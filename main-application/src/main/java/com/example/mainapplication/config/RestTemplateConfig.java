package com.example.mainapplication.config;



import com.example.axon.service.JwtAuthenticationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;

/**
 * Configuration for RestTemplate with JWT authentication for service-to-service calls
 */
@Configuration
public class RestTemplateConfig {

//    @Bean
//    public RestTemplate restTemplate() {
//        return new RestTemplate();
//    }

    @Bean
    public RestTemplate authenticatedRestTemplate(JwtAuthenticationService jwtAuthenticationService) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(Collections.singletonList(
            new JwtAuthenticationInterceptor(jwtAuthenticationService)
        ));
        return restTemplate;
    }

    /**
     * Interceptor that adds JWT token to outgoing requests
     */
    public static class JwtAuthenticationInterceptor implements ClientHttpRequestInterceptor {

        private final JwtAuthenticationService jwtAuthenticationService;

        public JwtAuthenticationInterceptor(JwtAuthenticationService jwtAuthenticationService) {
            this.jwtAuthenticationService = jwtAuthenticationService;
        }

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws IOException {

            // Only add token for internal service calls (not for Keycloak token requests)
            String uri = request.getURI().toString();
            if (!uri.contains("/protocol/openid-connect/token")) {
                try {
                    String token = jwtAuthenticationService.getServiceToken();
                    request.getHeaders().setBearerAuth(token);
                } catch (Exception e) {
                    // Log the error but don't fail the request
                    // This allows for graceful degradation if Keycloak is unavailable
                    System.err.println("Failed to add JWT token to request: " + e.getMessage());
                }
            }

            return execution.execute(request, body);
        }
    }
}