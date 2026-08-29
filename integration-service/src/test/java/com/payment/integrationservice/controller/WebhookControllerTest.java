package com.payment.integrationservice.controller;

import com.payment.integrationservice.service.WebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebhookControllerTest {

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static class RecordingWebhookService implements WebhookService {
        String lastPayload;
        String lastSignature;
        String lastSecret;

        @Override
        public Map<String, Object> handleStripeWebhook(String payload, String signatureHeader, String endpointSecret) {
            this.lastPayload = payload;
            this.lastSignature = signatureHeader;
            this.lastSecret = endpointSecret;
            // Return a predictable body the controller should echo back
            return Map.of("ok", true, "provider", "stripe", "received", payload != null);
        }
    }

    @Test
    @DisplayName("TC-INT 001 stripeWebhookPassesPayloadSignature")
    void stripeWebhookPassesPayloadSignature() throws Exception {
        // Arrange
        RecordingWebhookService svc = new RecordingWebhookService();
        WebhookController controller = new WebhookController(svc);
        setField(controller, "stripeWebhookSecret", "whsec_test_123");

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        String payload = """
        {
          "id": "evt_123",
          "object": "event",
          "type": "payment_intent.succeeded"
        }
        """;
        String signature = "t=1234567890,v1=abcdef";

        // Act & Assert (HTTP contract)
        mvc.perform(post("/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ok", is(true)))
                .andExpect(jsonPath("$.provider", is("stripe")))
                .andExpect(jsonPath("$.received", is(true)));

        // Assert (controller → service wiring)
        assertEquals(payload.replaceAll("\\s+", ""), svc.lastPayload.replaceAll("\\s+", ""), "Payload should be forwarded as-is");
        assertEquals(signature, svc.lastSignature, "Stripe-Signature header should be forwarded");
        assertEquals("whsec_test_123", svc.lastSecret, "Injected webhook secret should be forwarded");
    }

    @Test
    @DisplayName("TC-INT-002 stripeWebhookAllowsMissingSignature")
    void stripeWebhookAllowsMissingSignature() throws Exception {
        RecordingWebhookService svc = new RecordingWebhookService();
        WebhookController controller = new WebhookController(svc);
        setField(controller, "stripeWebhookSecret", "whsec_test_123");

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        String payload = "{\"hello\":\"world\"}";

        mvc.perform(post("/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok", is(true)));

        assertEquals(payload, svc.lastPayload);
        assertEquals(null, svc.lastSignature, "Signature may be null if header is absent");
        assertEquals("whsec_test_123", svc.lastSecret);
    }
}
