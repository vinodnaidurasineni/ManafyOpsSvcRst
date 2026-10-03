package com.manafy.ops.common.dto;

import java.util.List;

/**
 * Standard collection envelope (Artifact #3 §56): {@code data} plus paging
 * {@code meta} (page, pageSize, total). Default pageSize 25, max 100 (§54, §107).
 */
public class PageResponse<T> {

    // Business rule: list/search APIs must never return more than 10 records in
    // a single response. Both the default AND the hard max are 10, so every
    // paginated endpoint that funnels through clampPageSize() is capped — and a
    // client requesting pageSize=100 still receives at most 10.
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 10;

    private List<T> data;
    private Meta meta;

    private PageResponse() {}

    public static <T> PageResponse<T> of(List<T> data, int page, int pageSize, long total) {
        PageResponse<T> r = new PageResponse<>();
        r.data = data;
        r.meta = new Meta(page, pageSize, total);
        return r;
    }

    /** Clamp a requested pageSize into [1, MAX_PAGE_SIZE], defaulting when unset. */
    public static int clampPageSize(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    /** Normalize a requested page to a 1-based value (default 1). */
    public static int normalizePage(Integer requested) {
        return (requested == null || requested < 1) ? 1 : requested;
    }

    public List<T> getData() { return data; }
    public Meta getMeta() { return meta; }

    public static class Meta {
        private final int page;
        private final int pageSize;
        private final long total;

        public Meta(int page, int pageSize, long total) {
            this.page = page;
            this.pageSize = pageSize;
            this.total = total;
        }

        public int getPage() { return page; }
        public int getPageSize() { return pageSize; }
        public long getTotal() { return total; }
    }
}
