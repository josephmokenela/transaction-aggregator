package io.mokenela.transactionaggregator.domain.port.in;

import java.util.List;

/**
 * Generic wrapper for paginated results returned from use cases.
 *
 * @param content       the items on the current page
 * @param page          zero-based page number that was requested
 * @param size          page size that was requested
 * @param totalElements total number of items across all pages
 * @param totalPages    total number of pages for the given size
 */
public record PagedResponse<T>(
        List<T> content,
        long page,
        long size,
        long totalElements,
        long totalPages
) {}
