package com.restapi;

import com.restapi.webhooks.WebhookSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AsyncJobsAndWebhooksTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Async Jobs: POST /jobs returns 202 Accepted, Location, and Retry-After")
    void testAsyncJobPattern() throws Exception {
        // 1. Submit long-running async job
        String location = mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXCEL_EXPORT\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Retry-After", is("2")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getHeader("Location");

        // 2. Poll progress
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()));

        // Wait brief moment for Virtual Thread to execute
        Thread.sleep(500);

        // 3. Verify progress completed
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.progressPercent", is(100)))
                .andExpect(jsonPath("$.resultUrl", notNullValue()));
    }

    @Test
    @DisplayName("Webhook Inbound: Valid HMAC signature returns 200 OK; invalid returns 401")
    void testWebhookHmacVerification() throws Exception {
        String secret = "whsec_super_secret_test_key_12345";
        byte[] payload = "{\"event\":\"order.paid\",\"orderId\":\"ord_1\"}".getBytes(StandardCharsets.UTF_8);

        String validSignature = WebhookSigner.computeSignature(payload, secret);

        // Valid signature passes
        mockMvc.perform(post("/api/v1/webhooks/incoming")
                        .header("X-Signature-256", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        // Forged/invalid signature fails with 401 Unauthorized
        mockMvc.perform(post("/api/v1/webhooks/incoming")
                        .header("X-Signature-256", "sha256=invalidforgedsignature12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }
}
