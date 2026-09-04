package com.restapi.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Generic Cursor Paginated Collection Response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CursorPage<T>(
        List<T> data,
        String nextCursor,
        boolean hasMore,
        int count
) {
    public CursorPage {
        data = List.copyOf(data);
    }
}
