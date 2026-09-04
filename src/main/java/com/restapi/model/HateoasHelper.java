package com.restapi.model;

import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates Richardson Maturity Level 3 state-driven hypermedia links.
 */
public final class HateoasHelper {

    private HateoasHelper() {}

    public static Map<String, HateoasLink> buildOrderLinks(String baseUrl, Order order) {
        String selfUrl = baseUrl + "/api/v1/orders/" + order.id();
        Map<String, HateoasLink> links = new LinkedHashMap<>();

        links.put("self", new HateoasLink(selfUrl, HttpMethod.GET, "Get order details"));

        switch (order.status()) {
            case PENDING -> {
                links.put("pay", new HateoasLink(selfUrl + "/pay", HttpMethod.POST, "Pay for this order"));
                links.put("cancel", new HateoasLink(selfUrl + "/cancel", HttpMethod.POST, "Cancel this order"));
                links.put("update", new HateoasLink(selfUrl, HttpMethod.PUT, "Update this order (requires If-Match)"));
            }
            case PAID -> {
                links.put("cancel", new HateoasLink(selfUrl + "/cancel", HttpMethod.POST, "Cancel and refund order"));
            }
            case CANCELLED, COMPLETED -> {
                // Terminal states: only self link is available
            }
        }

        return links;
    }

    public static HalResource<Order> wrapWithHal(String baseUrl, Order order) {
        return new HalResource<>(order, buildOrderLinks(baseUrl, order));
    }
}
