package com.landfall.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

public record PageResult<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasMore,
        String cursor) {

    public PageResult {
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> of(List<T> items, PageQuery query, long total) {
        Objects.requireNonNull(query, "query must not be null");
        boolean hasMore = (long) query.page() * query.pageSize() < total;
        return new PageResult<>(items, query.page(), query.pageSize(), total, hasMore, null);
    }
}
