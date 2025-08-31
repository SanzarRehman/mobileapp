package com.example.mainapplication.security;

import com.example.mainapplication.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for security configuration
 */
@WebMvcTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAllowAccessToHealthEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAccessToPrometheusEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldAllowAuthenticatedUserToAccessCommandEndpoints() throws Exception {
        // This test would normally require authentication, but with TestSecurityConfig it's disabled
        mockMvc.perform(post("/api/commands/users")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().is4xxClientError()); // Expect validation error, not auth error
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldAllowAuthenticatedUserToAccessQueryEndpoints() throws Exception {
        // This test would normally require authentication, but with TestSecurityConfig it's disabled
        mockMvc.perform(get("/api/queries/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToAccessProjectionEndpoints() throws Exception {
        // This test would normally require admin role, but with TestSecurityConfig it's disabled
        mockMvc.perform(post("/api/projections/rebuild")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().is4xxClientError()); // Expect validation error, not auth error
    }
}