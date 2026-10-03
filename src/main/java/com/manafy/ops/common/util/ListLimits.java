package com.manafy.ops.common.util;

import java.util.List;

/**
 * Central limit for list/search API responses that do NOT flow through
 * {@link com.manafy.ops.common.dto.PageResponse} (e.g. compat-layer lists and
 * unbounded sub-resource lists returned as a bare {@code List<...>}).
 *
 * Business rule: a list/search response must never contain more than 10 records.
 * Paginated endpoints already enforce this via PageResponse.clampPageSize; this
 * helper covers the remaining non-paginated list endpoints.
 */
public final class ListLimits {

    public static final int MAX = 10;

    private ListLimits() {}

    /** Truncate any list to the first {@link #MAX} items (never returns null). */
    public static <T> List<T> cap(List<T> list) {
        if (list == null) return List.of();
        return list.size() <= MAX ? list : list.subList(0, MAX);
    }
}
