package com.restapi.pagination;

import com.restapi.model.Order;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Applies cursor-based pagination and filtering over streams of Orders.
 */
public final class CursorPaginationHelper {

    private CursorPaginationHelper() {}

    public static CursorPage<Order> paginate(
            List<Order> allOrders,
            Optional<Cursor> cursorOpt,
            String statusFilter,
            int limit
    ) {
        // 1. Filter by status using Java Streams
        var stream = allOrders.stream()
                .filter(o -> statusFilter == null || statusFilter.isBlank() || o.status().name().equalsIgnoreCase(statusFilter.trim()));

        // 2. Sort by createdAt DESC, id DESC
        Comparator<Order> comparator = Comparator.comparing(Order::createdAt).reversed().thenComparing(Order::id).reversed();
        List<Order> sorted = stream.sorted(comparator).toList();

        // 3. Apply Cursor seek (skip items until cursor is passed)
        int startIndex = 0;
        if (cursorOpt.isPresent()) {
            Cursor cursor = cursorOpt.get();
            for (int i = 0; i < sorted.size(); i++) {
                Order order = sorted.get(i);
                if (order.createdAt().equals(cursor.timestamp()) && order.id().equals(cursor.id())) {
                    startIndex = i + 1; // Start immediately after cursor
                    break;
                }
            }
        }

        List<Order> slice = sorted.stream()
                .skip(startIndex)
                .limit(limit + 1L)
                .toList();

        boolean hasMore = slice.size() > limit;
        List<Order> pageData = hasMore ? slice.subList(0, limit) : slice;

        String nextCursor = null;
        if (hasMore && !pageData.isEmpty()) {
            Order lastOrder = pageData.get(pageData.size() - 1);
            nextCursor = new Cursor(lastOrder.createdAt(), lastOrder.id()).encode();
        }

        return new CursorPage<>(pageData, nextCursor, hasMore, pageData.size());
    }
}
