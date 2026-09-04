package com.restapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpMethod;

/**
 * HATEOAS Hypermedia Link representation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HateoasLink(
        String href,
        String method,
        String title
) {
    public HateoasLink(String href, HttpMethod method, String title) {
        this(href, method.name(), title);
    }
}
