package com.payment.integrationservice.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.payment.integrationservice.controller.IntegrationPaymentsController;
import com.payment.integrationservice.controller.WebhookController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-only tests.
 * - No controllers are loaded.
 * - Verifies SecurityFilterChain behavior.
 */
@WebMvcTest(
        controllers = {}, // ← do not load any controllers
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        IntegrationPaymentsController.class,
                        WebhookController.class
                })
        }
)
@Import(SecurityConfig.class) // just your security configuration
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.jwt.secret=test-secret-123" // used by JwtAuthFilter
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    private String bearer(String sub) {
        return "Bearer " + JWT.create()
                .withIssuer("payment-service")
                .withSubject(sub)
                .sign(Algorithm.HMAC256("test-secret-123"));
    }

    @Test
    @DisplayName("TC-INT-018 integrationsMissingAuthUnauthorized")
    void integrationsMissingAuthUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/integrations/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-INT-019 integrationsWithValidJwtNot401")
    void integrationsWithValidJwtNot401() throws Exception {
        var res = mockMvc.perform(post("/v1/integrations/payments")
                        .header("Authorization", bearer("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        // We only test security; with no controllers loaded this will likely be 404, but must NOT be 401.
        assertThat(res.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("TC-INT-020 webhooksOpenNot401")
    void webhooksOpenNot401() throws Exception {
        var res = mockMvc.perform(post("/v1/webhooks/stripe")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("raw"))
                .andReturn();

        // Webhooks are permitted by security; without controllers it's typically 404, but not 401.
        assertThat(res.getResponse().getStatus()).isNotEqualTo(401);
    }
}
