package com.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restapi.controller.OrderController;
import com.restapi.observability.CorrelationIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /orders/{id} returns HATEOAS _links and ETag header")
    void testGetOrderHateoasAndETag() throws Exception {
        mockMvc.perform(get("/api/v1/orders/ord_1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(jsonPath("$.data.id", is("ord_1")))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                // Richardson Level 3 HATEOAS links
                .andExpect(jsonPath("$._links.self.href", notNullValue()))
                .andExpect(jsonPath("$._links.pay.href", notNullValue()))
                .andExpect(jsonPath("$._links.cancel.href", notNullValue()))
                .andExpect(jsonPath("$._links.update.href", notNullValue()));
    }

    @Test
    @DisplayName("Conditional GET with matching If-None-Match returns 304 Not Modified")
    void testConditionalGet304() throws Exception {
        // First fetch to get ETag
        String etag = mockMvc.perform(get("/api/v1/orders/ord_1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        // Second fetch with If-None-Match
        mockMvc.perform(get("/api/v1/orders/ord_1")
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));
    }

    @Test
    @DisplayName("POST /orders creates order with 201 Created and Location header")
    void testCreateOrder() throws Exception {
        var req = new OrderController.CreateOrderRequest(new BigDecimal("89.99"), "USD");

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andExpect(jsonPath("$.data.amount", is(89.99)));
    }

    @Test
    @DisplayName("PUT /orders/{id} with mismatched If-Match returns 412 Precondition Failed (RFC 7807)")
    void testOptimisticLockingPreconditionFailed() throws Exception {
        var req = new OrderController.UpdateOrderRequest(new BigDecimal("199.99"));

        mockMvc.perform(put("/api/v1/orders/ord_1")
                        .header("If-Match", "\"stale-etag-value\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.title", is("Precondition Failed")))
                .andExpect(jsonPath("$.status", is(412)))
                .andExpect(jsonPath("$.detail", containsString("ETag mismatch")));
    }

    @Test
    @DisplayName("POST /orders/{id}/pay transitions to PAID; second pay returns 422 Unprocessable Entity")
    void testStateTransitionsAnd422() throws Exception {
        // 1. Pay pending order ord_1
        mockMvc.perform(post("/api/v1/orders/ord_1/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PAID")))
                .andExpect(jsonPath("$._links.pay").doesNotExist()) // pay link removed in PAID state!
                .andExpect(jsonPath("$._links.cancel.href", notNullValue())); // cancel (refund) still available

        // 2. Paying again is semantically invalid -> returns 422 Unprocessable Content
        mockMvc.perform(post("/api/v1/orders/ord_1/pay"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.title", is("Unprocessable Entity")))
                .andExpect(jsonPath("$.status", is(422)));
    }

    @Test
    @DisplayName("GET /orders returns cursor-paginated list")
    void testCursorPagination() throws Exception {
        mockMvc.perform(get("/api/v1/orders?limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.count", is(1)));
    }
}
