package com.restapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * HAL (Hypertext Application Language) wrapper delivering Richardson Maturity Level 3 HATEOAS.
 */
public record HalResource<T>(
        T data,
        @JsonProperty("_links") Map<String, HateoasLink> links
) {}
