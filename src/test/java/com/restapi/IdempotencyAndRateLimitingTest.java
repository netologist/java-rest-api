package com.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restapi.controller.OrderController;
import com.restapi.idempotency.IdempotencyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyAndRateLimitingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("IdempotencyFilter replays cached response with Idempotency-Replay: true")
    void testIdempotentReplay() throws Exception {
        var req = new OrderController.CreateOrderRequest(new BigDecimal("45.50"), "USD");
        String idempotencyKey = "idemp-key-order-12345";

        // First attempt: creates new order
        mockMvc.perform(post("/api/v1/orders")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replay"))
                .andExpect(jsonPath("$.data.id", notNullValue()));

        // Second attempt with same key: replays cached 201 response with Idempotency-Replay header!
        mockMvc.perform(post("/api/v1/orders")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replay", is("true")))
                .andExpect(jsonPath("$.data.id", notNullValue()));
    }

    @Test
    @DisplayName("RateLimitFilter populates standard RateLimit-* headers")
    void testRateLimitingHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(header().string("RateLimit-Limit", is("100")))
                .andExpect(header().exists("RateLimit-Remaining"))
                .andExpect(header().string("RateLimit-Reset", is("1")));
    }
}
