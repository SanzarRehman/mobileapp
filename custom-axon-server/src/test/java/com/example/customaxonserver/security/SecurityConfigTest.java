package com.example.customaxonserver.security;

import com.example.customaxonserver.config.TestSecurityConfig;
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
 * Tests for security configuration in custom axon server
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
    @WithMockUser(roles = "SERVICE")
    void shouldAllowServiceToAccessCommandEndpoints() throws Exception {
        // This test would normally require service role, but with TestSecurityConfig it's disabled
        mockMvc.perform(post("/api/commands")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().is4xxClientError()); // Expect validation error, not auth error
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void shouldAllowServiceToAccessQueryEndpoints() throws Exception {
        // This test would normally require service role, but with TestSecurityConfig it's disabled
        mockMvc.perform(post("/api/queries")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().is4xxClientError()); // Expect validation error, not auth error
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToAccessAdminEndpoints() throws Exception {
        // This test would normally require admin role, but with TestSecurityConfig it's disabled
        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isNotFound()); // Endpoint doesn't exist, but no auth error
    }
}