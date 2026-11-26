package com.landfall.common.api;

public record PageQuery(int page, int pageSize) {

    public PageQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
    }

    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
